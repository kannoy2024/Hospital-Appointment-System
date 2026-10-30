package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.system.common.BusinessException;
import com.system.common.MoneyUtils;
import com.system.dto_vo.dto.PrescriptionCreateRequest;
import com.system.dto_vo.dto.PrescriptionItemRequest;
import com.system.dto_vo.vo.PaymentPrepayVO;
import com.system.dto_vo.vo.PrescriptionItemVO;
import com.system.dto_vo.vo.PrescriptionVO;
import com.system.entity.Appointment;
import com.system.entity.Medicine;
import com.system.entity.PaymentOrder;
import com.system.entity.Prescription;
import com.system.entity.PrescriptionItem;
import com.system.mapper.AppointmentMapper;
import com.system.mapper.MedicineMapper;
import com.system.mapper.PrescriptionItemMapper;
import com.system.mapper.PrescriptionMapper;
import com.system.service.NotificationService;
import com.system.service.payment.PaymentService;
import com.system.service.PrescriptionService;
import com.system.service.WalletService;
import com.system.service.payment.model.PrepayCommand;
import com.system.service.payment.model.PrepayResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处方服务实现：负责处方开具与重开、药费支付、标记取药，联动开方预扣库存与预约状态流转。
 */
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final MedicineMapper medicineMapper;
    private final AppointmentMapper appointmentMapper;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final PaymentService paymentService;

    /** 处方药费支付单有效期（分钟）：药费不该 30 分钟就作废，默认 24 小时 */
    @Value("${app.prescription.pay-expire-minutes:1440}")
    private int payExpireMinutes;

    /**
     * 开具或重开处方：重开时先归还旧处方库存，药费大于 0 转为待缴药费、否则直接完成就诊。
     *
     * @param appointment 就诊预约
     * @param doctorId 开方医生ID
     * @param request 处方创建请求
     * @return 处方VO
     */
    @Override
    @Transactional
    public PrescriptionVO createForAppointment(Appointment appointment, Long doctorId, PrescriptionCreateRequest request) {
        if (!"in_consultation".equals(appointment.getStatus())) {
            throw new BusinessException("仅就诊中可开具处方");
        }
        Prescription existing = findByAppointmentId(appointment.getId());
        if (existing != null && !"cancelled".equals(existing.getStatus())) {
            restoreStock(existing.getId());
            prescriptionItemMapper.delete(new LambdaQueryWrapper<PrescriptionItem>()
                    .eq(PrescriptionItem::getPrescriptionId, existing.getId()));
            prescriptionMapper.deleteById(existing.getId());
        }

        Prescription prescription = new Prescription();
        prescription.setAppointmentId(appointment.getId());
        prescription.setUserId(appointment.getUserId());
        prescription.setDoctorId(doctorId);
        prescription.setStatus("unpaid");
        prescription.setTotalAmountFen(0L);
        prescriptionMapper.insert(prescription);

        long total = 0L;
        List<PrescriptionItem> items = new ArrayList<>();
        for (PrescriptionItemRequest itemReq : request.getItems()) {
            Medicine medicine = medicineMapper.selectById(itemReq.getMedicineId());
            if (medicine == null || medicine.getStatus() != 1) {
                throw new BusinessException("药品不存在或已停用");
            }
            if (medicine.getStock() < itemReq.getQuantity()) {
                throw new BusinessException("药品库存不足：" + medicine.getName());
            }
            long amount = Math.multiplyExact(medicine.getPriceFen(), itemReq.getQuantity());
            PrescriptionItem item = new PrescriptionItem();
            item.setPrescriptionId(prescription.getId());
            item.setMedicineId(medicine.getId());
            item.setMedicineName(medicine.getName());
            item.setSpec(medicine.getSpec());
            item.setDosage(itemReq.getDosage());
            item.setFrequency(itemReq.getFrequency());
            item.setDays(itemReq.getDays());
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPriceFen(medicine.getPriceFen());
            item.setAmountFen(amount);
            prescriptionItemMapper.insert(item);
            items.add(item);
            total += amount;
        }

        reserveStock(items);

        prescription.setTotalAmountFen(total);
        prescriptionMapper.updateById(prescription);

        if (total > 0) {
            appointment.setStatus("pending_rx_pay");
            appointmentMapper.updateById(appointment);
            notificationService.notifyUser(appointment.getUserId(), "prescription_unpaid", "待缴药费",
                    String.format("医生已为您开具处方，药费 %s，请尽快在小程序支付。", MoneyUtils.formatYuan(total)),
                    "appointment", appointment.getId());
        } else {
            prescription.setStatus("dispensed");
            prescriptionMapper.updateById(prescription);
            appointment.setStatus("completed");
            appointmentMapper.updateById(appointment);
            notificationService.notifyUser(appointment.getUserId(), "prescription_dispensed", "就诊完成",
                    "本次就诊无需用药，已自动完成。欢迎对医生进行评价。",
                    "appointment", appointment.getId());
        }
        return toVO(prescription, items);
    }

    /**
     * 按预约ID查询处方（含明细）。
     *
     * @param appointmentId 预约ID
     * @return 处方VO，不存在返回 null
     */
    @Override
    public PrescriptionVO getByAppointmentId(Long appointmentId) {
        Prescription prescription = findByAppointmentId(appointmentId);
        if (prescription == null) {
            return null;
        }
        return toVO(prescription, listItems(prescription.getId()));
    }

    /**
     * 查询患者可见的处方，校验处方归属当前用户。
     *
     * @param appointmentId 预约ID
     * @param userId 当前用户ID
     * @return 处方VO，不存在或无权查看返回 null
     */
    @Override
    public PrescriptionVO getVisibleForPatient(Long appointmentId, Long userId) {
        Prescription prescription = findByAppointmentId(appointmentId);
        if (prescription == null || !prescription.getUserId().equals(userId)) {
            return null;
        }
        return toVO(prescription, listItems(prescription.getId()));
    }

    /**
     * 余额支付药费，仅更新处方为已支付，预约保持待取药直至标记取药。
     *
     * <p>微信/支付宝不再走这里：必须经 {@code /prepay} 建支付单，由回调推进状态。
     *
     * @param prescriptionId 处方ID
     * @param userId 支付用户ID
     * @param payMethod 支付方式（仅 balance）
     * @return 支付后的处方VO
     */
    @Override
    @Transactional
    public PrescriptionVO payPrescription(Long prescriptionId, Long userId, String payMethod) {
        Prescription prescription = requirePrescription(prescriptionId);
        if (!prescription.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权支付该处方");
        }
        if (!"unpaid".equals(prescription.getStatus())) {
            throw new BusinessException("处方状态不可支付");
        }
        Appointment appointment = appointmentMapper.selectById(prescription.getAppointmentId());
        if (appointment == null || !"pending_rx_pay".equals(appointment.getStatus())) {
            throw new BusinessException("当前预约状态不可支付处方");
        }
        String method = normalizePayMethod(payMethod);
        if (!"balance".equals(method)) {
            throw new BusinessException("微信/支付宝支付请改用预支付接口：POST /api/prescriptions/"
                    + prescriptionId + "/prepay");
        }
        LocalDateTime payTime = LocalDateTime.now();
        int updated = prescriptionMapper.update(null, new LambdaUpdateWrapper<Prescription>()
                .eq(Prescription::getId, prescriptionId)
                .eq(Prescription::getStatus, "unpaid")
                .set(Prescription::getStatus, "paid")
                .set(Prescription::getPayMethod, method)
                .set(Prescription::getPayTime, payTime));
        if (updated == 0) {
            throw new BusinessException("处方状态不可支付");
        }
        if ("balance".equals(method)) {
            walletService.payForPrescription(userId, prescriptionId, prescription.getTotalAmountFen());
            // ★余额已扣成功，立刻关掉 prepay 阶段留下的微信支付单。
            //   否则那张单仍可付，用户会被收两次药费；而回调到达时处方已是 paid，
            //   PrescriptionPayHandler.markPaid 的 CAS 只会打一条 error 日志、不退款。
            //   关单触发的 onPayClosed 对处方只记日志（处方没有过期作废语义），安全。
            closeCreatedPaymentOrder(prescriptionId);
        }
        notificationService.notifyUser(userId, "prescription_paid", "药费支付成功",
                String.format("处方药费 %s 已支付成功，请前往药房取药，取药后由医院确认。", MoneyUtils.formatYuan(prescription.getTotalAmountFen())),
                "appointment", prescription.getAppointmentId());
        notificationService.notifyDoctor(prescription.getDoctorId(), "prescription_paid_doctor", "患者已缴费",
                String.format("患者已支付处方药费 %s，请核对后标记取药。", MoneyUtils.formatYuan(prescription.getTotalAmountFen())),
                "appointment", prescription.getAppointmentId());
        return toVO(requirePrescription(prescriptionId), listItems(prescriptionId));
    }

    /**
     * 发起处方药费预支付：只建支付单，处方状态一分不动，等支付回调才推进。
     *
     * <p>校验与 {@link #payPrescription} 完全一致（归属 / 处方 unpaid / 预约 pending_rx_pay），
     * 区别只是把「收钱」交给支付模块。
     *
     * @param prescriptionId 处方ID
     * @param userId 支付用户ID
     * @param payMethod 支付方式（wechat / alipay）
     * @return 预支付返回体（含 outTradeNo 与调起支付参数）
     */
    @Override
    @Transactional
    public PaymentPrepayVO prepay(Long prescriptionId, Long userId, String payMethod) {
        Prescription prescription = requirePrescription(prescriptionId);
        if (!prescription.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权支付该处方");
        }
        if (!"unpaid".equals(prescription.getStatus())) {
            throw new BusinessException("处方状态不可支付");
        }
        Appointment appointment = appointmentMapper.selectById(prescription.getAppointmentId());
        if (appointment == null || !"pending_rx_pay".equals(appointment.getStatus())) {
            throw new BusinessException("当前预约状态不可支付处方");
        }
        String method = normalizePayMethod(payMethod);
        if ("balance".equals(method)) {
            throw new BusinessException("余额支付请调用 POST /api/prescriptions/" + prescriptionId + "/pay");
        }
        if (prescription.getTotalAmountFen() <= 0) {
            throw new BusinessException("处方金额异常，无法发起支付");
        }

        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(payExpireMinutes);

        PrepayCommand command = new PrepayCommand();
        command.setBizType(PaymentOrder.BIZ_PRESCRIPTION);
        command.setBizId(prescriptionId);
        command.setUserId(userId);
        command.setAmountFen(prescription.getTotalAmountFen());
        command.setPayMethod(method);
        command.setDescription("处方药费");
        command.setExpireTime(expireTime);

        PrepayResult result = paymentService.prepay(command);
        PaymentOrder order = paymentService.getEntityByOutTradeNo(result.getOutTradeNo());

        PaymentPrepayVO vo = new PaymentPrepayVO();
        vo.setBizType(PaymentOrder.BIZ_PRESCRIPTION);
        vo.setBizId(prescriptionId);
        vo.setOutTradeNo(result.getOutTradeNo());
        vo.setPayMethod(method);
        vo.setAmountFen(prescription.getTotalAmountFen());
        vo.setBizStatus(prescription.getStatus());
        vo.setExpireTime(order == null ? expireTime : order.getExpireTime());
        vo.setPayParams(result.getPayParams());
        return vo;
    }

    /**
     * 关掉该处方名下未完成的支付单（如果有）。
     *
     * <p>只在「余额支付成功」后调用：余额已经把药费收掉了，还留在 CREATED 的微信单
     * 就是一颗定时炸弹——用户再付一次，钱就收重了。
     *
     * @param prescriptionId 处方ID
     */
    private void closeCreatedPaymentOrder(Long prescriptionId) {
        String outTradeNo = paymentService.findCreatedOutTradeNo(
                PaymentOrder.BIZ_PRESCRIPTION, prescriptionId);
        if (outTradeNo == null) {
            return;
        }
        // closeIfCreated 内部已吞掉网关异常（关不掉就保留 CREATED 交给对账），这里不需要再兜
        paymentService.closeIfCreated(outTradeNo);
    }

    /**
     * 标记取药，已取药时幂等处理，成功后联动预约完成。
     *
     * @param prescriptionId 处方ID
     */
    @Override
    @Transactional
    public void dispensePrescription(Long prescriptionId) {
        Prescription prescription = requirePrescription(prescriptionId);
        if ("dispensed".equals(prescription.getStatus())) {
            completeAppointmentIfPendingRx(prescription.getAppointmentId());
            return;
        }
        if (!"paid".equals(prescription.getStatus())) {
            throw new BusinessException("仅已支付处方可标记取药");
        }
        int updated = prescriptionMapper.update(null, new LambdaUpdateWrapper<Prescription>()
                .eq(Prescription::getId, prescriptionId)
                .eq(Prescription::getStatus, "paid")
                .set(Prescription::getStatus, "dispensed"));
        if (updated == 0) {
            throw new BusinessException("处方状态不可标记取药");
        }
        completeAppointmentIfPendingRx(prescription.getAppointmentId());
        notificationService.notifyUser(prescription.getUserId(), "prescription_dispensed", "取药完成",
                "您已完成取药，本次就诊结束。欢迎对医生进行评价。",
                "appointment", prescription.getAppointmentId());
    }

    /**
     * 按处方ID查询处方（含明细）。
     *
     * @param id 处方ID
     * @return 处方VO
     */
    @Override
    public PrescriptionVO getById(Long id) {
        Prescription prescription = requirePrescription(id);
        return toVO(prescription, listItems(id));
    }

    /**
     * 校验预约能否完成就诊：存在待支付或待取药处方时抛出业务异常。
     *
     * @param appointment 就诊预约
     */
    @Override
    public void validateCanComplete(Appointment appointment) {
        Prescription prescription = findByAppointmentId(appointment.getId());
        if (prescription == null) {
            return;
        }
        if ("unpaid".equals(prescription.getStatus())) {
            throw new BusinessException("处方待支付，无法完成就诊");
        }
        if ("paid".equals(prescription.getStatus())) {
            throw new BusinessException("处方待取药，请先标记取药");
        }
    }

    /**
     * 预约处于待取药状态时将其置为已完成。
     *
     * @param appointmentId 预约ID
     */
    private void completeAppointmentIfPendingRx(Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment != null && "pending_rx_pay".equals(appointment.getStatus())) {
            appointment.setStatus("completed");
            appointmentMapper.updateById(appointment);
        }
    }

    /**
     * 开方时原子扣减库存，失败则抛出业务异常使整单回滚。
     *
     * @param items 处方明细列表
     */
    private void reserveStock(List<PrescriptionItem> items) {
        for (PrescriptionItem item : items) {
            int updated = medicineMapper.tryDecrementStock(item.getMedicineId(), item.getQuantity());
            if (updated == 0) {
                Medicine medicine = medicineMapper.selectById(item.getMedicineId());
                String name = medicine != null ? medicine.getName() : "药品";
                throw new BusinessException("药品库存不足：" + name);
            }
        }
    }

    /**
     * 重开处方前归还已预扣的药品库存。
     *
     * @param prescriptionId 处方ID
     */
    private void restoreStock(Long prescriptionId) {
        for (PrescriptionItem item : listItems(prescriptionId)) {
            medicineMapper.incrementStock(item.getMedicineId(), item.getQuantity());
        }
    }

    /**
     * 按预约ID查询未取消的处方。
     *
     * @param appointmentId 预约ID
     * @return 处方实体，不存在返回 null
     */
    private Prescription findByAppointmentId(Long appointmentId) {
        return prescriptionMapper.selectOne(new LambdaQueryWrapper<Prescription>()
                .eq(Prescription::getAppointmentId, appointmentId)
                .ne(Prescription::getStatus, "cancelled"));
    }

    /**
     * 查询处方的明细列表。
     *
     * @param prescriptionId 处方ID
     * @return 处方明细列表
     */
    private List<PrescriptionItem> listItems(Long prescriptionId) {
        return prescriptionItemMapper.selectList(new LambdaQueryWrapper<PrescriptionItem>()
                .eq(PrescriptionItem::getPrescriptionId, prescriptionId));
    }

    /**
     * 查询处方，不存在则抛出业务异常。
     *
     * @param id 处方ID
     * @return 处方实体
     */
    private Prescription requirePrescription(Long id) {
        Prescription prescription = prescriptionMapper.selectById(id);
        if (prescription == null) {
            throw new BusinessException("处方不存在");
        }
        return prescription;
    }

    /**
     * 将处方与明细实体组装为 VO。
     *
     * @param prescription 处方实体
     * @param items 处方明细列表
     * @return 处方VO
     */
    private PrescriptionVO toVO(Prescription prescription, List<PrescriptionItem> items) {
        PrescriptionVO vo = PrescriptionVO.fromEntity(prescription);
        vo.setItems(items.stream().map(PrescriptionItemVO::fromEntity).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 规范化支付方式，校验必须是 balance / wechat / alipay 之一。
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
