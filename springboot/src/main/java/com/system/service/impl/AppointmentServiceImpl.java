package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.*;
import com.system.dto_vo.dto.AppointmentCreateRequest;
import com.system.dto_vo.dto.AppointmentReviewRequest;
import com.system.dto_vo.vo.AppointmentPrepayVO;
import com.system.dto_vo.vo.AppointmentVO;
import com.system.dto_vo.vo.PatientVO;
import com.system.dto_vo.vo.PrescriptionVO;
import com.system.dto_vo.vo.RefundOrderVO;
import com.system.entity.*;
import com.system.mapper.*;
import com.system.security.SecurityUtils;
import com.system.service.*;
import com.system.service.payment.PaymentService;
import com.system.service.payment.model.PrepayCommand;
import com.system.service.payment.model.PrepayResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static com.system.common.RedisConstants.APPOINTMENT_LOCK_PREFIX;


/**
 * 预约挂号服务实现：负责预约的创建、支付、取消、签到、就诊流转与评价等全生命周期业务。
 */
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentServiceImpl.class);

    // 挂号费默认 2000 分（20.00 元），服务端统一定价，不信任前端传入
    // 如需调整，在 application.yml 配 app.appointment.default-fee-fen
    @Value("${app.appointment.default-fee-fen:2000}")
    private long defaultFeeFen;

    private final AppointmentMapper appointmentMapper;
    private final AppointmentReviewMapper appointmentReviewMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final WalletService walletService;
    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;
    private final NotificationService notificationService;
    private final ScheduleCacheService scheduleCacheService;
    private final ResilientRedisClient resilientRedisClient;
    private final PatientService patientService;
    private final PaymentService paymentService;


    @Value("${app.appointment.unpaid-expire-minutes:30}")
    private int unpaidExpireMinutes;
    // ==================== 创建预约 ====================

    /**
     * 根据就诊人信息与请求参数拼装一条待支付状态的预约实体。
     *
     * @param patientId 就诊人ID
     * @param patientName 就诊人姓名
     * @param patientPhone 就诊人手机号
     * @param request 预约创建请求
     * @param user 当前登录用户
     * @param feeFen 挂号费（单位：分），由调用方从配置读取后传入（本方法为 static，不能直接读实例字段）
     * @return 待支付状态的预约实体
     */
    private static Appointment getAppointment(Long patientId,
                                              String patientName,
                                              String patientPhone,
                                              AppointmentCreateRequest request,
                                              User user,
                                              long feeFen) {
        Appointment appointment = new Appointment();
        appointment.setUserId(user.getId());
        appointment.setPatientId(patientId);
        appointment.setScheduleId(request.getScheduleId());
        appointment.setPatientName(patientName);
        appointment.setPatientPhone(patientPhone);
        appointment.setRemark(request.getRemark());
        appointment.setAmountFen(feeFen);
        appointment.setStatus("unpaid");
        return appointment;
    }

    /**
     * 创建预约：加分布式锁防重、校验排班与重复预约、扣减号源并生成待支付订单，同时写入延迟队列。
     *
     * @param request 预约创建请求（含排班ID、就诊人信息、备注）
     * @return 新建（或复用的未支付）预约 VO
     */
    @Override
    @Transactional
    public AppointmentVO create(AppointmentCreateRequest request) {
        // 1. 获取当前用户
        User user = SecurityUtils.getCurrentUserEntity();

        //Redis 分布式锁。key 的粒度是 用户ID:排班ID，确保同一个用户不能同时对同一个排班发起两次请求
        String lockKey = APPOINTMENT_LOCK_PREFIX + user.getId() + ":" + request.getScheduleId();
        Boolean locked = resilientRedisClient.setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("操作太频繁，请稍后再试");
        }

        Schedule schedule = null;
        boolean reserved = false;
        try {
            // 2 解析就诊人信息
            Long patientId = null;
            String patientName, patientPhone;

            if (request.getPatientId() != null) {
                // 新流程：查就诊人 + 校验归属（不是自己的会抛 403）
                PatientVO patient = patientService.getOwned(request.getPatientId(), user.getId());
                patientId = patient.getId();
                patientName = patient.getName();
                patientPhone = patient.getPhone();
            } else {
                // 老流程（灰度期兼容）：直接用前端传的
                patientName = request.getPatientName();
                patientPhone = request.getPatientPhone();
                if (!StringUtils.hasText(patientName)) {
                    throw new BusinessException("请选择就诊人");
                }
            }

            // 2. 校验排班有效性
            schedule = scheduleMapper.selectById(request.getScheduleId());
            if (schedule == null) {
                throw new BusinessException("排班不存在");
            }
            if (schedule.getStatus() != 1) {
                throw new BusinessException("该排班已停诊");
            }
            if (schedule.getScheduleDate().isBefore(LocalDate.now())) {
                throw new BusinessException("不能预约已过期的排班");
            }
            if (schedule.getScheduleDate().isEqual(LocalDate.now())) {
                LocalTime endTime = parseEndTime(schedule.getTimeSlot());
                if (LocalTime.now().isAfter(endTime)) {
                    throw new BusinessException("该排班的就诊时间已过，请重新选择");
                }
            }

            LambdaQueryWrapper<Appointment> dupWrapper = new LambdaQueryWrapper<Appointment>()
                    .eq(Appointment::getScheduleId, request.getScheduleId())
                    .in(Appointment::getStatus, "unpaid", "pending", "confirmed",
                            "checked_in", "in_consultation", "pending_rx_pay")
                    .orderByDesc(Appointment::getCreateTime);

            if (patientId != null) {
                // 新流程：按就诊人维度判重（给父亲和母亲挂同一号是合理的）
                dupWrapper.eq(Appointment::getPatientId, patientId);
            } else {
                // 老流程（灰度期）：退回按用户维度判重，保持原有行为
                dupWrapper.eq(Appointment::getUserId, user.getId());
            }

            List<Appointment> existingList = appointmentMapper.selectList(dupWrapper);

            if (!existingList.isEmpty()) {
                Appointment existing = existingList.getFirst();

                if ("unpaid".equals(existing.getStatus())) {
                    //使用，用户新传的表单填充
                    existing.setRemark(request.getRemark());
                    appointmentMapper.updateById(existing);

                    try {
                        long expireAt = System.currentTimeMillis() + unpaidExpireMinutes * 60 * 1000L;
                        resilientRedisClient.zAdd(RedisConstants.DELAY_APPOINTMENT_KEY,
                                String.valueOf(existing.getId()), expireAt);
                    } catch (Exception e) {
                        log.error("刷新延迟队列失败, appointmentId:{}", existing.getId(), e);
                    }
                    return toVO(existing);
                }

                //最后才判断是不是重复下单
                throw new BusinessException("该就诊人已预约此号源，请勿重复预约");
            }


            // 4. Redis + Lua 扣减号源（降级走 MySQL 乐观锁）
            reserved = scheduleCacheService.tryReserveSlot(schedule.getId());
            int updated = scheduleMapper.tryIncrementBookedCount(schedule.getId());
            if (!reserved) {
                if (updated == 0) throw new BusinessException("号源已满");
            } else {
                // Redis 扣减成功 → 同步 MySQL 落账
                if (updated == 0) {
                    scheduleCacheService.releaseSlot(schedule.getId());  // 回滚 Redis
                    reserved = false; //发生回滚，标志重置
                    throw new BusinessException("号源已满");
                }
            }

            // 5. 拼装预约订单的详细信息
            Appointment appointment = getAppointment(patientId, patientName, patientPhone
                    , request, user, defaultFeeFen);
            appointmentMapper.insert(appointment);

            // 预约创建成功 → 加入 Redis 延迟队列，30 分钟后触发检查
            try {
                long expireAt = System.currentTimeMillis() + unpaidExpireMinutes * 60 * 1000L;  // 当前时间 + 30分钟
                resilientRedisClient.zAdd(RedisConstants.DELAY_APPOINTMENT_KEY, String.valueOf(appointment.getId()), expireAt);

            } catch (Exception e) {
                // 加入队列失败不能影响预约主流程，只记日志
                log.error("加入延迟队列失败, appointmentId:{}", appointment.getId(), e);
            }
            return toVO(appointment);


        } catch (Exception e) {
            // 回滚 Redis 号源（仅当已成功扣减时）
            if (reserved) {
                scheduleCacheService.releaseSlot(schedule.getId());
            }
            throw e;
        } finally {
            resilientRedisClient.delete(lockKey);
        }
    }

    /**
     * 支付挂号费（余额支付专用）：以条件更新保证幂等（仅 unpaid 可支付），
     * 扣减钱包余额后同步通知用户。
     *
     * <p>**微信/支付宝不在这里支付**：外部通道必须先建支付单、拿支付参数，
     * 由网关回调驱动状态流转（见 {@link #prepay(Long, String)}）。
     * 以前这里对 wechat/alipay 直接写 payTime 并置 pending，那只是“假装付成功了”，
     * 钱其实一分没收到——S2 起彻底删除这条路径。
     *
     * @param id 预约ID
     * @param payMethod 支付方式，仅接受 balance
     * @return 支付后的预约 VO
     */
    @Override
    @Transactional
    public AppointmentVO payAppointment(Long id, String payMethod) {
        // 1. 获取当前用户并校验权限
        User user = SecurityUtils.getCurrentUserEntity();

        Appointment appointment = requireAppointment(id);
        if (!appointment.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "错误，这不是你的订单，你无权支付该预约");
        }

        // 2. 规范化支付方式，并把外部通道挡回去
        String method = normalizePayMethod(payMethod);
        if (!"balance".equals(method)) {
            throw new BusinessException(
                    "微信/支付宝支付请改用预支付接口：POST /api/appointments/" + id + "/prepay");
        }
        LocalDateTime payTime = LocalDateTime.now();

        // 3. 条件更新：只有 unpaid 状态才能支付（防止重复支付）
        int updated = appointmentMapper.update(null, new LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, id)                // 条件1：ID匹配
                .eq(Appointment::getUserId, user.getId())  // 条件2：用户匹配
                .eq(Appointment::getStatus, "unpaid")      // 条件3：当前状态必须是 unpaid（关键！）
                .set(Appointment::getStatus, "pending")    // 更新：状态改为 pending
                .set(Appointment::getPayMethod, method)    // 更新：支付方式
                .set(Appointment::getPayTime, payTime));   // 更新：支付时间

        if (updated == 0) {
            throw new BusinessException("订单状态不可支付");
        }

        // 4. 余额扣款：WHERE balance_fen >= amount_fen 的乐观条件扣款，余额不足会抛异常并整体回滚
        walletService.payByBalance(user.getId(), id, appointment.getAmountFen());

        // 4.1 ★余额已扣成功，必须立刻关掉 prepay 阶段留下的微信支付单。
        //     否则那张单仍然可付，用户「余额 + 微信」会被收两次钱；而回调到达时预约已是 pending，
        //     AppointmentPayHandler.markPaid 的 CAS 只会打一条 error 日志、不会退款 —— 这笔钱就再也退不回来。
        //     关单触发的 onPayClosed 会因预约已非 unpaid 而安全空转（见 AppointmentPayHandler#onPayClosed）。
        closePaymentOrderIfAny(id);

        // 5. 已支付，立刻从延迟队列摘掉，避免超时任务在边界时刻再扫到它
        try {
            resilientRedisClient.zRemove(RedisConstants.DELAY_APPOINTMENT_KEY, String.valueOf(id));
        } catch (Exception e) {
            log.error("移除延迟队列失败, appointmentId:{}", id, e);
        }

        // 6. 通知用户预约成功
        AppointmentVO vo = toVO(requireAppointment(id));
        notifyAppointmentPaid(vo);
        return vo;
    }

    /**
     * 发起预支付：为预约创建支付单，返回调起支付所需的参数。
     *
     * <p>本方法**不修改预约状态**——预约仍是 unpaid。只有网关回调到达后，
     * 才由 {@code AppointmentPayHandler.markPaid} 用 CAS 把它推进到 pending。
     * 这样「钱到账」与「预约可就诊」就由支付单这一个事实源串起来了。
     *
     * <p>可重入：同一预约重复点支付不会产生多条可支付单（PaymentService 会复用未过期的 CREATED 单）。
     *
     * @param id 预约ID
     * @param payMethod 支付方式（wechat/alipay）
     * @return 预支付参数
     */
    @Override
    @Transactional
    public AppointmentPrepayVO prepay(Long id, String payMethod) {
        User user = SecurityUtils.getCurrentUserEntity();

        Appointment appointment = requireAppointment(id);
        if (!appointment.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "错误，这不是你的订单，你无权支付该预约");
        }
        if (!"unpaid".equals(appointment.getStatus())) {
            throw new BusinessException("当前状态不可支付：" + appointment.getStatus());
        }

        String method = normalizePayMethod(payMethod);
        if ("balance".equals(method)) {
            // 余额是内部账本（balance_log 记流水），不经过支付网关，避免同一笔钱出现两张账本
            throw new BusinessException("余额支付请调用 /api/appointments/" + id + "/pay");
        }

        PrepayCommand command = new PrepayCommand();
        command.setBizType(PaymentOrder.BIZ_APPOINTMENT);
        command.setBizId(id);
        command.setUserId(user.getId());
        command.setAmountFen(appointment.getAmountFen());
        command.setPayMethod(method);
        command.setDescription("挂号费-" + appointment.getPatientName());
        // JSAPI 必传 openid；Mock 通道用不到，但先带上，S5 换真微信时零改动
        command.setPayerOpenid(user.getOpenid());
        // 支付单过期时间与预约超时取消时间同源，避免出现「预约已取消、支付单还能付」
        command.setExpireTime(LocalDateTime.now().plusMinutes(unpaidExpireMinutes));

        PrepayResult result = paymentService.prepay(command);

        PaymentOrder order = paymentService.getEntityByOutTradeNo(result.getOutTradeNo());

        AppointmentPrepayVO vo = new AppointmentPrepayVO();
        vo.setAppointmentId(id);
        vo.setOutTradeNo(result.getOutTradeNo());
        vo.setPayMethod(method);
        vo.setAmountFen(appointment.getAmountFen());
        vo.setAppointmentStatus(appointment.getStatus());
        vo.setExpireTime(order != null ? order.getExpireTime() : command.getExpireTime());
        vo.setPayParams(result.getPayParams());

        log.info("预约{}发起预支付，outTradeNo={} payMethod={} amountFen={}",
                id, result.getOutTradeNo(), method, appointment.getAmountFen());
        return vo;
    }

    // ==================== 患者端查询 ====================

    /**
     * 分页查询当前用户的预约列表，按前端 Tab 语义映射状态并二次过滤处方状态。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param status 前端 Tab 状态（pending/visiting/pending_dispense/pending_rx_pay/all）
     * @return 预约分页结果
     */
    @Override
    public PageResult<AppointmentVO> pageMine(long current,
                                              long size,
                                              String status) {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();

        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId)
                .orderByDesc(Appointment::getCreateTime);

        // 状态映射（前端 Tab 与后端状态不完全一致）
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            switch (status) {
                case "pending" ->
                    // "待就诊" Tab：包含 pending 和 confirmed
                        wrapper.in(Appointment::getStatus, "pending", "confirmed");
                case "visiting" ->
                    // "就诊中" Tab：包含签到、就诊中、待缴药费
                        wrapper.in(Appointment::getStatus, "checked_in", "in_consultation", "pending_rx_pay");
                case "pending_dispense" ->
                    // "待取药" Tab：处方已支付但未取药
                        wrapper.eq(Appointment::getStatus, "pending_rx_pay");
                default -> wrapper.eq(Appointment::getStatus, status);
            }
        }

        // 执行分页查询
        Page<Appointment> page = appointmentMapper.selectPage(new Page<>(current, size), wrapper);

        // 转为 VO 并过滤（pending_dispense 只显示处方已支付的）
        List<AppointmentVO> records = page.getRecords().stream()
                .map(this::toVO)
                .filter(vo -> !"pending_dispense".equals(status)
                        || ("pending_rx_pay".equals(vo.getStatus()) && "paid".equals(vo.getPrescriptionStatus())))
                .filter(vo -> !"pending_rx_pay".equals(status)
                        || vo.getPrescriptionStatus() == null
                        || "unpaid".equals(vo.getPrescriptionStatus()))
                .collect(Collectors.toList());

        // 特殊状态需要重新计算总条数（因为过滤了数据）
        long total = ("pending_dispense".equals(status) || "pending_rx_pay".equals(status))
                ? records.size()
                : page.getTotal();

        return new PageResult<>(records, total, page.getCurrent(), page.getSize());
    }

    /**
     * 查询当前用户的一条预约详情，并校验归属。
     *
     * @param id 预约ID
     * @return 预约 VO
     */
    @Override
    public AppointmentVO getMineById(Long id) {
        Appointment appointment = requireAppointment(id);
        if (!appointment.getUserId().equals(SecurityUtils.getCurrentUserEntity().getId())) {
            throw new BusinessException(403, "无权查看该预约");
        }
        return toVO(appointment);
    }

    // ==================== 取消预约 ====================

    /**
     * 患者取消自己的预约，校验归属后走统一的取消流程。
     *
     * @param id 预约ID
     */
    @Override
    @Transactional
    public void cancelByUser(Long id) {
        Appointment appointment = requireAppointment(id);
        if (!appointment.getUserId().equals(SecurityUtils.getCurrentUserEntity().getId())) {
            throw new BusinessException(403, "无权取消该预约");
        }
        doCancel(appointment);
    }

    // ==================== 管理端查询 ====================

    /**
     * 管理端分页查询预约列表，支持状态、就诊日期与患者姓名/手机号关键字过滤。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param status 状态过滤
     * @param scheduleDate 就诊日期过滤
     * @param keyword 患者姓名或手机号关键字
     * @return 预约分页结果
     */
    @Override
    public PageResult<AppointmentVO> pageAdmin(long current,
                                               long size,
                                               String status,
                                               LocalDate scheduleDate,
                                               String keyword) {
        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<Appointment>()
                .orderByDesc(Appointment::getCreateTime);

        // 状态过滤：pending_dispense 是前端伪状态，落库值仍是 pending_rx_pay
        if (StringUtils.hasText(status)) {
            if ("pending_dispense".equals(status)) {
                wrapper.eq(Appointment::getStatus, "pending_rx_pay");
                // 待取药 = 药费已缴但还没发药
                wrapper.apply("EXISTS (SELECT 1 FROM prescription p "
                        + "WHERE p.appointment_id = appointment.id AND p.status = 'paid')");
            } else if ("pending_rx_pay".equals(status)) {
                wrapper.eq(Appointment::getStatus, "pending_rx_pay");
                // 待缴药费 = 没有处方，或处方还没缴
                wrapper.apply("NOT EXISTS (SELECT 1 FROM prescription p "
                        + "WHERE p.appointment_id = appointment.id AND p.status IN ('paid','dispensed'))");
            } else {
                wrapper.eq(Appointment::getStatus, status);
            }
        }

        // 就诊日期在 schedule 表，用子查询下推，避免翻页后再过滤
        if (scheduleDate != null) {
            wrapper.apply("schedule_id IN (SELECT id FROM schedule WHERE schedule_date = {0})", scheduleDate);
        }

        // 关键字（患者姓名/手机号）
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Appointment::getPatientName, kw)
                    .or().like(Appointment::getPatientPhone, kw));
        }

        Page<Appointment> page = appointmentMapper.selectPage(new Page<>(current, size), wrapper);

        // 过滤条件已全部下推到 SQL，这里不再二次过滤，total 直接可信
        List<AppointmentVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 管理员取消预约，走统一的取消流程。
     *
     * @param id 预约ID
     */
    @Override
    @Transactional
    public void cancelByAdmin(Long id) {
        doCancel(requireAppointment(id));
    }

    // ==================== 完成就诊 ====================

    /**
     * 管理员标记就诊完成：待缴药费按处方状态配药，待就诊直接完成，就诊中需病历完整且处方已处理。
     *
     * @param id 预约ID
     */
    @Override
    @Transactional
    public void completeByAdmin(Long id) {
        Appointment appointment = requireAppointment(id);

        // 情况1：待缴药费状态 → 处理处方
        if ("pending_rx_pay".equals(appointment.getStatus())) {
            PrescriptionVO prescription = prescriptionService.getByAppointmentId(id);
            if (prescription == null) {
                throw new BusinessException("当前预约无处方记录");
            }
            if ("unpaid".equals(prescription.getStatus())) {
                throw new BusinessException("患者尚未支付处方，无法完成");
            }
            if ("paid".equals(prescription.getStatus())) {
                // 配药
                prescriptionService.dispensePrescription(prescription.getId());
                return;
            }
            if ("dispensed".equals(prescription.getStatus())) {
                appointment.setStatus("completed");
                appointmentMapper.updateById(appointment);
                return;
            }
            throw new BusinessException("处方状态异常");
        }

        // 情况2：待就诊状态 → 直接完成（无处方场景）
        if ("pending".equals(appointment.getStatus()) || "confirmed".equals(appointment.getStatus())) {
            appointment.setStatus("completed");
            appointmentMapper.updateById(appointment);
            return;
        }

        // 情况3：就诊中状态 → 需要病历完整 + 处方已处理
        if ("in_consultation".equals(appointment.getStatus())) {
            consultationService.requireConsultationComplete(id);  // 校验病历完整
            prescriptionService.validateCanComplete(appointment); // 校验处方状态
            appointment.setStatus("completed");
            appointmentMapper.updateById(appointment);
            return;
        }

        throw new BusinessException("当前状态不可标记完成");
    }

    // ==================== 签到 ====================

    /**
     * 患者到院签到：仅就诊日截止时间前 1 小时内可签到，成功后通知患者与医生。
     *
     * @param id 预约ID
     */
    @Override
    @Transactional
    public void checkInByUser(Long id) {
        User user = SecurityUtils.getCurrentUserEntity();

        //校验预约归属权
        Appointment appointment = requireAppointment(id);
        if (!appointment.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "无权操作该预约");
        }

        // 只有 pending/confirmed 才能签到
        if (!"pending".equals(appointment.getStatus()) && !"confirmed".equals(appointment.getStatus())) {
            throw new BusinessException("当前状态不可签到");
        }

        // 获取排班信息
        Schedule schedule = scheduleMapper.selectById(appointment.getScheduleId());
        if (schedule == null) {
            throw new BusinessException("排班不存在");
        }

        // 签到时间校验

        // 1. 解析结束时间作为截止时间
        //    兼容格式 "09:00" → 结束时间 09:00
        LocalTime cutoffTime = parseEndTime(schedule.getTimeSlot());

        // 2. 计算截止时间 = 就诊日期 + 结束时间
        LocalDateTime deadline = LocalDateTime.of(schedule.getScheduleDate(), cutoffTime);

        // 3. 签到开始时间 = 截止时间 - 60分钟
        LocalDateTime signInStart = deadline.minusMinutes(60);

        // 4. 当前时间
        LocalDateTime now = LocalDateTime.now();

        // 5. 校验签到时间范围 → 用户友好提示
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        if (now.isBefore(signInStart)) {
            throw new BusinessException("签到尚未开始，请在 " + schedule.getScheduleDate()+"   "+ signInStart.format(timeFormatter) + " 之后签到");
        }

        if (now.isAfter(deadline)) {
            throw new BusinessException("签到已截止，请联系医院处理");
        }

        // 更新状态
        appointment.setStatus("checked_in");
        appointmentMapper.updateById(appointment);

        // 通知患者和医生
        AppointmentVO vo = toVO(appointment);
        notificationService.notifyUser(appointment.getUserId(), "check_in_success", "签到成功",
                String.format("您已到院签到（%s %s），请等待医生接诊。", vo.getScheduleDate(), vo.getTimeSlot()),
                "appointment", appointment.getId());
        notificationService.notifyDoctor(schedule.getDoctorId(), "patient_checked_in", "患者到院",
                String.format("患者%s已到院签到，就诊时间 %s %s。",
                        appointment.getPatientName(), vo.getScheduleDate(), vo.getTimeSlot()),
                "appointment", appointment.getId());
    }

    /**
     * 从 timeSlot 中解析结束时间，兼容 "09:00-11:00" 与 "09:00" 两种格式。
     *
     * @param timeSlot 时间段字符串
     * @return 结束时间
     */
    private LocalTime parseEndTime(String timeSlot) {
        // 1. 空值校验
        if (!StringUtils.hasText(timeSlot)) {
            throw new BusinessException("系统配置错误：排班时间段为空");
        }

        String trimmed = timeSlot.trim();

        // 2. 包含 "-" → 完整格式，取后半部分
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-");
            if (parts.length != 2 || !StringUtils.hasText(parts[1])) {
                throw new BusinessException("系统配置错误：排班时间段格式异常");
            }
            String endTimeStr = parts[1].trim();
            try {
                return LocalTime.parse(endTimeStr);
            } catch (Exception e) {
                throw new BusinessException("系统配置错误：无法解析排班结束时间");
            }
        }
        // 3. 不包含 "-" → 整体作为结束时间（兼容旧数据）
        try {
            return LocalTime.parse(trimmed);
        } catch (Exception e) {
            throw new BusinessException("系统配置错误：无法解析排班时间段");
        }
    }


    // ==================== 医生接诊 ====================

    /**
     * 医生开始接诊：校验排班归属与已签到状态，流转为就诊中并通知患者。
     *
     * @param appointmentId 预约ID
     * @param doctorId 医生ID
     */
    @Override
    @Transactional
    public void startConsultation(Long appointmentId, Long doctorId) {

        Appointment appointment = requireAppointment(appointmentId);

        Schedule schedule = requireScheduleOwned(appointment.getScheduleId(), doctorId);

        if (!"checked_in".equals(appointment.getStatus())) {
            throw new BusinessException("请患者先到院签到后再接诊");
        }

        appointment.setStatus("in_consultation");
        appointmentMapper.updateById(appointment);

        AppointmentVO vo = toVO(appointment);
        notificationService.notifyUser(appointment.getUserId(), "consultation_started", "开始接诊",
                String.format("%s医生已开始为您接诊，请配合问诊。", vo.getDoctorName()),
                "appointment", appointment.getId());
    }


    /**
     * 医生完成就诊：校验排班归属、病历完整与处方状态，满足条件后流转为已完成。
     *
     * @param appointmentId 预约ID
     * @param doctorId 医生ID
     */
    @Override
    @Transactional
    public void completeClinical(Long appointmentId, Long doctorId) {
        Appointment appointment = requireAppointment(appointmentId);
        requireScheduleOwned(appointment.getScheduleId(), doctorId);

        if (!"in_consultation".equals(appointment.getStatus())) {
            throw new BusinessException("当前状态不可完成就诊");
        }

        // 校验病历完整
        consultationService.requireConsultationComplete(appointmentId);

        // 检查是否有处方
        PrescriptionVO prescription = prescriptionService.getByAppointmentId(appointmentId);
        if (prescription == null) {
            // 无处方 → 直接完成
            appointment.setStatus("completed");
            appointmentMapper.updateById(appointment);
            return;
        }

        // 有处方 → 校验处方状态，然后完成
        prescriptionService.validateCanComplete(appointment);
        appointment.setStatus("completed");
        appointmentMapper.updateById(appointment);
    }

    // ==================== 评价 ====================

    /**
     * 提交就诊评价：仅已完成且未评价过的预约可评价。
     *
     * @param appointmentId 预约ID
     * @param request 评价请求（评分、标签、评论）
     */
    @Override
    @Transactional
    public void submitReview(Long appointmentId, AppointmentReviewRequest request) {
        User user = SecurityUtils.getCurrentUserEntity();
        Appointment appointment = requireAppointment(appointmentId);

        if (!appointment.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "无权评价该预约");
        }
        if (!"completed".equals(appointment.getStatus())) {
            throw new BusinessException("仅已完成就诊的预约可评价");
        }

        // 防重复评价
        Long exists = appointmentReviewMapper.selectCount(
                new LambdaQueryWrapper<AppointmentReview>()
                        .eq(AppointmentReview::getAppointmentId, appointmentId));
        if (exists != null && exists > 0) {
            throw new BusinessException("您已评价过该预约");
        }

        // 获取排班信息（为了拿到医生ID）
        Schedule schedule = scheduleMapper.selectById(appointment.getScheduleId());
        if (schedule == null) {
            throw new BusinessException("排班不存在");
        }

        // 保存评价
        AppointmentReview review = new AppointmentReview();
        review.setAppointmentId(appointmentId);
        review.setUserId(user.getId());
        review.setDoctorId(schedule.getDoctorId());
        review.setRating(request.getRating());
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            review.setTags(String.join(",", request.getTags()));  // 标签用逗号分隔存储
        }
        review.setComment(request.getComment());
        appointmentReviewMapper.insert(review);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 取消预约核心逻辑：未支付直接取消并释放号源，已支付待就诊取消并退款，其他状态不可取消。
     *
     * <p>未支付时**必须先关掉可能存在的支付单**（用户可能已经点开过收银台）：
     * 关单成功后，取消预约、回补号源、发通知全部由 {@code AppointmentPayHandler.onPayClosed}
     * 完成，这里直接返回，绝不能再做第二遍（否则号源会被回补两次）。
     *
     * @param appointment 待取消的预约
     */
    private void doCancel(Appointment appointment) {
        String status = appointment.getStatus();

        // 未支付：先关单，再释放号源
        if ("unpaid".equals(status)) {
            if (closePaymentOrderIfAny(appointment.getId())) {
                // 支付单已关闭 → handler 已完成「unpaid → cancelled + 回补号源 + 通知」
                return;
            }
            // ★CAS 而不是 updateById：并发重复取消时只有一次能命中。
            //   否则 decrementBookedCount / releaseSlot 会被执行两次，号源越取消越多。
            int affected = appointmentMapper.update(null, new LambdaUpdateWrapper<Appointment>()
                    .eq(Appointment::getId, appointment.getId())
                    .eq(Appointment::getStatus, "unpaid")
                    .set(Appointment::getStatus, "cancelled"));
            if (affected == 0) {
                log.info("[Cancel] 预约{}的未支付状态已被并发取消，跳过", appointment.getId());
                return;
            }
            appointment.setStatus("cancelled");
            scheduleMapper.decrementBookedCount(appointment.getScheduleId());
            scheduleCacheService.releaseSlot(appointment.getScheduleId());
            notifyAppointmentCancelled(appointment);
            // 用户手动取消时，从延迟队列移除被锁定的订单
            resilientRedisClient.zRemove(RedisConstants.DELAY_APPOINTMENT_KEY, String.valueOf(appointment.getId()));
            return;
        }

        // 已支付待就诊：取消并退款
        if (!"pending".equals(status) && !"confirmed".equals(status)) {
            throw new BusinessException("当前状态不可取消");
        }

        boolean refundBalance = "balance".equals(appointment.getPayMethod());
        // ★CAS 而不是 updateById：这是防「重复退款」的第一道闸。
        //   两个并发的取消请求都读到 pending，若无条件更新则两边都会走到下面的退款逻辑，
        //   余额会被退两次（微信路径有 out_refund_no 唯一键兜底，余额路径没有）。
        //   条件用读到的原状态 status（pending 或 confirmed），只有一次能命中。
        int cancelled = appointmentMapper.update(null, new LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, appointment.getId())
                .eq(Appointment::getStatus, status)
                .set(Appointment::getStatus, "cancelled"));
        if (cancelled == 0) {
            log.info("[Cancel] 预约{}的状态已从{}被并发修改，跳过取消与退款",
                    appointment.getId(), status);
            return;
        }
        appointment.setStatus("cancelled");
        scheduleMapper.decrementBookedCount(appointment.getScheduleId());
        scheduleCacheService.releaseSlot(appointment.getScheduleId());

        if (refundBalance) {
            walletService.refundForAppointment(
                    appointment.getUserId(), appointment.getId(), appointment.getAmountFen());
        } else if (StringUtils.hasText(appointment.getPayMethod())) {
            // ★微信/支付宝：走支付单退款（异步，网关回调才算最终成功）
            //
            // 这里刻意 try/catch 吞掉异常：用户点「取消」必须成功，退款是异步补偿。
            // 若退款没发起成功，payment_order 仍是 PAID、预约已是 cancelled，
            // 下一轮 PaymentReconcileService.sweepPaidButBizCancelled() 会把它捞出来补退。
            try {
                String outTradeNo = paymentService.findPaidOutTradeNo(
                        PaymentOrder.BIZ_APPOINTMENT, appointment.getId());
                if (outTradeNo == null) {
                    // 兼容 S2 之前「假支付」的历史数据：没有支付单，只能人工退款
                    log.warn("预约{}为{}支付但找不到 PAID 支付单（历史数据），需人工退款",
                            appointment.getId(), appointment.getPayMethod());
                } else {
                    RefundOrderVO refund = paymentService.refund(outTradeNo, "预约取消退款");
                    notifyAppointmentRefundStarted(appointment, refund);
                }
            } catch (Exception e) {
                // 取消不能被退款失败拖垮，交给对账任务兜底
                log.error("[Refund] ★预约{}取消失败于发起退款，取消已生效，退款交由对账任务重试",
                        appointment.getId(), e);
            }
        }
        notifyAppointmentCancelled(appointment);
        // 用户手动取消时，从延迟队列移除被锁定的订单
        resilientRedisClient.zRemove(RedisConstants.DELAY_APPOINTMENT_KEY, String.valueOf(appointment.getId()));

    }

    /**
     * 关掉该预约名下未完成的支付单（如果有）。
     *
     * @param appointmentId 预约ID
     * @return true 表示确实关掉了一张 CREATED 支付单，业务侧回滚已由 handler 完成
     */
    private boolean closePaymentOrderIfAny(Long appointmentId) {
        String outTradeNo = paymentService.findCreatedOutTradeNo(PaymentOrder.BIZ_APPOINTMENT, appointmentId);
        return outTradeNo != null && paymentService.closeIfCreated(outTradeNo);
    }

    // ==================== 通知方法 ====================

    /**
     * 向患者推送预约支付成功通知。
     *
     * @param vo 预约 VO
     */
    private void notifyAppointmentPaid(AppointmentVO vo) {
        notificationService.notifyUser(vo.getUserId(), "appointment_paid", "预约成功",
                String.format("您已成功预约%s %s，就诊时间 %s %s，请按时到院签到。",
                        vo.getDepartmentName(), vo.getDoctorName(), vo.getScheduleDate(), vo.getTimeSlot()),
                "appointment", vo.getId());
    }

    /**
     * 向患者推送预约取消通知，余额支付时附带退款提示。
     *
     * @param appointment 已取消的预约
     */
    private void notifyAppointmentCancelled(Appointment appointment) {
        AppointmentVO vo = toVO(appointment);
        String refundHint = "balance".equals(appointment.getPayMethod()) ? "挂号费已退回账户余额。" : "";
        notificationService.notifyUser(appointment.getUserId(), "appointment_cancelled", "预约已取消",
                String.format("您的预约（%s %s %s）已取消。%s",
                        vo.getDepartmentName(), vo.getScheduleDate(), vo.getTimeSlot(), refundHint),
                "appointment", appointment.getId());
    }

    /**
     * 向患者推送「退款处理中」通知（微信/支付宝支付的预约取消）。
     *
     * <p>文案刻意用「正在退回」而不是「已退回」：真实退款是异步的，
     * 最终结果要等网关回调，前端可轮询 {@code GET /api/payments/{outTradeNo}/refund}。
     *
     * @param appointment 已取消的预约
     * @param refund      退款单（可能为空）
     */
    private void notifyAppointmentRefundStarted(Appointment appointment, RefundOrderVO refund) {
        AppointmentVO vo = toVO(appointment);
        String amount = refund != null && refund.getAmountFen() != null
                ? MoneyUtils.formatYuan(refund.getAmountFen()) : "";
        notificationService.notifyUser(appointment.getUserId(), "appointment_refund", "退款处理中",
                String.format("您的预约（%s %s %s）已取消，%s 正在按原支付方式退回，预计 1-3 个工作日到账。",
                        vo.getDepartmentName(), vo.getScheduleDate(), vo.getTimeSlot(), amount),
                "appointment", appointment.getId());
    }

    // ==================== 查询与映射方法 ====================
    /**
     * 按 ID 查询预约，不存在则抛出异常。
     *
     * @param id 预约ID
     * @return 预约实体
     */
    private Appointment requireAppointment(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BusinessException("预约不存在");
        }
        return appointment;
    }

    /**
     * 查询排班并校验其归属指定医生。
     *
     * @param scheduleId 排班ID
     * @param doctorId 医生ID
     * @return 排班实体
     */
    private Schedule requireScheduleOwned(Long scheduleId, Long doctorId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || !doctorId.equals(schedule.getDoctorId())) {
            throw new BusinessException(403, "无权操作该预约");
        }
        return schedule;
    }

    /**
     * 将预约实体转换为 VO，供外部按 ID 直接获取时调用。
     *
     * @param appointment 预约实体
     * @return 预约 VO
     */
    @Override
    public AppointmentVO mapToVO(Appointment appointment) {
        return toVO(appointment);
    }

    /**
     * 拼装预约 VO：联查排班、医生、科室，并附加是否已评价、处方信息与是否有病历。
     *
     * @param appointment 预约实体
     * @return 预约 VO
     */
    private AppointmentVO toVO(Appointment appointment) {
        Schedule schedule = scheduleMapper.selectById(appointment.getScheduleId());
        Doctor doctor = schedule != null ? doctorMapper.selectById(schedule.getDoctorId()) : null;
        Department dept = doctor != null ? departmentMapper.selectById(doctor.getDepartmentId()) : null;

        AppointmentVO vo = new AppointmentVO();
        vo.setId(appointment.getId());
        vo.setScheduleId(appointment.getScheduleId());
        vo.setUserId(appointment.getUserId());
        vo.setPatientId(appointment.getPatientId());
        vo.setPatientName(appointment.getPatientName());
        vo.setPatientPhone(PrivacyUtils.maskPhone(appointment.getPatientPhone()));
        vo.setAmountFen(appointment.getAmountFen());
        vo.setPayMethod(appointment.getPayMethod());
        vo.setPayMethodText(AppointmentVO.payMethodLabel(appointment.getPayMethod()));
        vo.setPayTime(appointment.getPayTime());
        vo.setStatus(appointment.getStatus());
        vo.setRemark(appointment.getRemark());
        vo.setCreateTime(appointment.getCreateTime());

        // 排班信息
        if (schedule != null) {
            vo.setScheduleDate(schedule.getScheduleDate());
            vo.setTimeSlot(schedule.getTimeSlot());
        }

        // 医生信息
        if (doctor != null) {
            vo.setDoctorId(doctor.getId());
            vo.setDoctorName(doctor.getName());
            vo.setDoctorTitle(doctor.getTitle());
        }

        // 科室信息
        if (dept != null) {
            vo.setDepartmentName(dept.getName());
        }

        // 是否已评价
        Long reviewCount = appointmentReviewMapper.selectCount(
                new LambdaQueryWrapper<AppointmentReview>()
                        .eq(AppointmentReview::getAppointmentId, appointment.getId()));
        vo.setEvaluated(reviewCount != null && reviewCount > 0);

        // 处方信息
        PrescriptionVO prescription = prescriptionService.getByAppointmentId(appointment.getId());
        if (prescription != null) {
            vo.setPrescriptionId(prescription.getId());
            vo.setPrescriptionStatus(prescription.getStatus());
            vo.setPrescriptionAmountFen(prescription.getTotalAmountFen());
        }

        // 是否有病历
        vo.setHasConsultation(consultationService.getByAppointmentId(appointment.getId()) != null);

        return vo;
    }

    /**
     * 规范化支付方式，仅允许 balance/wechat/alipay。
     *
     * @param payMethod 原始支付方式
     * @return 规范化后的支付方式
     */
    private String normalizePayMethod(String payMethod) {
        if (!StringUtils.hasText(payMethod)) {
            throw new BusinessException("请选择支付方式");
        }
        return switch (payMethod.trim().toLowerCase()) {
            case "balance", "wechat", "alipay" -> payMethod.trim().toLowerCase();
            default -> throw new BusinessException("不支持的支付方式");
        };
    }
}