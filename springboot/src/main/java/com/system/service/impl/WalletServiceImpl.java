package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.BusinessException;
import com.system.common.MoneyUtils;
import com.system.common.PageResult;
import com.system.dto_vo.vo.BalanceLogVO;
import com.system.dto_vo.vo.PaymentPrepayVO;
import com.system.dto_vo.dto.WalletRechargeRequest;
import com.system.entity.BalanceLog;
import com.system.entity.PaymentOrder;
import com.system.entity.User;
import com.system.mapper.BalanceLogMapper;
import com.system.mapper.UserMapper;
import com.system.service.payment.PaymentService;
import com.system.service.WalletService;
import com.system.service.payment.model.PrepayCommand;
import com.system.service.payment.model.PrepayResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 钱包服务实现：负责用户余额充值、支付、退款扣款及流水记录。
 *
 * <p>★充值的唯一加余额入口是 {@link #applyRecharge}，它只由支付回调调用；
 * 任何“先加余额，钱慢慢收”的写法都是资损。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    @Value("${app.recharge.max-fen:1000000}")
    private long maxRechargeFen;

    /** 充值支付单有效期（分钟） */
    @Value("${app.recharge.expire-minutes:30}")
    private int rechargeExpireMinutes;

    private final UserMapper userMapper;
    private final BalanceLogMapper balanceLogMapper;
    private final PaymentService paymentService;


    /**
     * 发起充值预支付：校验限额与支付方式、建支付单，返回调起支付参数。
     *
     * <p>此时余额一分不动，必须等支付回调调用 {@link #applyRecharge} 才入账。
     *
     * @param userId 用户ID
     * @param request 充值请求
     * @return 预支付返回体
     */
    @Override
    @Transactional
    public PaymentPrepayVO prepayRecharge(Long userId, WalletRechargeRequest request) {
        long amountFen = request.getAmountFen();
        if (amountFen > maxRechargeFen) {
            throw new BusinessException("单次充值不能超过:" + MoneyUtils.formatYuan(maxRechargeFen));
        }
        String payMethod = normalizeRechargePayMethod(request.getPayMethod());
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(rechargeExpireMinutes);

        PrepayCommand command = new PrepayCommand();
        command.setBizType(PaymentOrder.BIZ_RECHARGE);
        // 充值没有业务表，bizType + bizId 只能记到用户维度
        command.setBizId(userId);
        command.setUserId(userId);
        command.setAmountFen(amountFen);
        command.setPayMethod(payMethod);
        command.setDescription("账户充值");
        // ★充值必须关闭“复用未过期支付单”护栏，否则同一用户连充两笔会被合并成一笔
        command.setReuseExistingCreated(false);
        command.setExpireTime(expireTime);

        PrepayResult result = paymentService.prepay(command);
        PaymentOrder order = paymentService.getEntityByOutTradeNo(result.getOutTradeNo());

        PaymentPrepayVO vo = new PaymentPrepayVO();
        vo.setBizType(PaymentOrder.BIZ_RECHARGE);
        vo.setBizId(userId);
        vo.setOutTradeNo(result.getOutTradeNo());
        vo.setPayMethod(payMethod);
        vo.setAmountFen(amountFen);
        vo.setExpireTime(order == null ? expireTime : order.getExpireTime());
        vo.setPayParams(result.getPayParams());
        return vo;
    }

    /**
     * 充值到账：加余额并记一条 recharge 流水。只允许由支付回调调用。
     *
     * <p>幂等两道闸：
     * <ol>
     *   <li>payment_order 的 CAS（CREATED → PAID）保证 handler 只会被调一次；</li>
     *   <li>这里再按 (type='recharge', order_id=支付单id) 查一次流水，已有则跳过。</li>
     * </ol>
     *
     * @param userId 用户ID
     * @param paymentOrderId 支付单ID（写进流水 order_id）
     * @param amountFen 入账金额（只允许来自支付单，绝不读前端传值）
     * @param payMethod 支付方式
     */
    @Override
    @Transactional
    public void applyRecharge(Long userId, Long paymentOrderId, long amountFen, String payMethod) {
        if (amountFen <= 0) {
            throw new BusinessException("充值金额必须大于 0 分");
        }
        Long exists = balanceLogMapper.selectCount(new LambdaQueryWrapper<BalanceLog>()
                .eq(BalanceLog::getType, "recharge")
                .eq(BalanceLog::getOrderId, paymentOrderId));
        if (exists != null && exists > 0) {
            log.warn("[Wallet] 充值单{}已入账，跳过重复入账", paymentOrderId);
            return;
        }
        long balanceAfter = credit(userId, amountFen);
        saveLog(userId, "recharge", amountFen, balanceAfter, paymentOrderId, payMethod, "账户充值");
        log.info("[Wallet] 充值入账成功 userId={} amountFen={} 余额={} 支付单={}",
                userId, amountFen, balanceAfter, paymentOrderId);
    }

    /**
     * 扣除余额支付处方费用并记录流水。
     *
     * @param userId 用户ID
     * @param prescriptionId 处方ID
     * @param amountFen 支付金额
     */
    @Override
    @Transactional
    public void payForPrescription(Long userId, Long prescriptionId, long amountFen) {
        long balanceAfter = debit(userId, amountFen);
        saveLog(userId, "prescription_pay", amountFen, balanceAfter, prescriptionId, "balance", "处方余额支付");
    }

    /**
     * 扣除余额支付预约挂号费用并记录流水。
     *
     * @param userId 用户ID
     * @param appointmentId 预约ID
     * @param amountFen 支付金额
     */
    @Override
    @Transactional
    public void payByBalance(Long userId, Long appointmentId, long amountFen) {
        long balanceAfter = debit(userId, amountFen);
        saveLog(userId, "order_pay", amountFen, balanceAfter, appointmentId, "balance", "预约余额支付");
    }

    /**
     * 取消预约时退回余额并记录流水，金额无效时直接返回。
     *
     * @param userId 用户ID
     * @param appointmentId 预约ID
     * @param amountFen 退款金额
     */
    @Override
    @Transactional
    public void refundForAppointment(Long userId, Long appointmentId, long amountFen) {
        if (amountFen <= 0) {
            return;
        }
        // ★幂等闸：同一笔预约只能退一次。
        //   取消预约的入口（用户取消 / 管理员取消 / 超时取消）可能并发重入，
        //   第一道防线是 AppointmentServiceImpl.doCancel 里的状态 CAS，这里是第二道——
        //   只要流水里已有这笔预约的退款记录，就直接跳过，绝不重复加余额。
        Long refunded = balanceLogMapper.selectCount(new LambdaQueryWrapper<BalanceLog>()
                .eq(BalanceLog::getType, "order_refund")
                .eq(BalanceLog::getOrderId, appointmentId)
                .eq(BalanceLog::getUserId, userId));
        if (refunded != null && refunded > 0) {
            log.info("[Wallet] 预约{}已存在余额退款流水，跳过重复退款 userId={}", appointmentId, userId);
            return;
        }
        long balanceAfter = credit(userId, amountFen);
        saveLog(userId, "order_refund", amountFen, balanceAfter, appointmentId, "balance", "取消预约退款");
    }

    /**
     * 分页查询当前用户的余额流水。
     *
     * @param userId 用户ID
     * @param current 当前页码
     * @param size 每页条数
     * @return 余额流水分页结果
     */
    @Override
    public PageResult<BalanceLogVO> pageMyLogs(Long userId, long current, long size) {
        Page<BalanceLog> page = balanceLogMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<BalanceLog>()
                        .eq(BalanceLog::getUserId, userId)
                        .orderByDesc(BalanceLog::getCreateTime)
        );
        List<BalanceLogVO> records = page.getRecords().stream()
                .map(BalanceLogVO::fromEntity)
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 增加用户余额并返回加后的余额。
     *
     * @param userId 用户ID
     * @param amountFen 增加金额
     * @return 增加后的余额
     */
    private long credit(Long userId, long amountFen) {
        getUserOrThrow(userId);
        // ★必须在数据库侧自增，不能「读到内存 + 加 + 写回」。
        //   并发入账（比如充值到账与取消退款同时发生）时，两个线程都基于同一份快照算 balanceAfter，
        //   后写的会把先写的覆盖掉，用户凭空少钱，且流水里两条都记着「成功」。
        //   amountFen 是 long，拼进 SQL 只有数字字符，不存在注入。
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("balance_fen = balance_fen + " + amountFen));
        return getUserOrThrow(userId).getBalanceFen();
    }

    /**
     * 条件更新扣减用户余额，余额不足或并发冲突时抛出异常。
     *
     * @param userId 用户ID
     * @param amountFen 扣减金额
     * @return 扣减后的余额
     */
    private long debit(Long userId, long amountFen) {
        getUserOrThrow(userId);
        // ★守卫与自减必须落在同一条 UPDATE 里。原写法「先读快照算 balanceAfter，再 .ge(amountFen) 条件写回」
        //   在并发下会两个请求都通过 .ge 校验、却都写入同一个 balanceAfter —— 等于只扣了一次，用户多钱。
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .ge(User::getBalanceFen, amountFen)
                .setSql("balance_fen = balance_fen - " + amountFen));
        if (updated == 0) {
            throw new BusinessException("余额不足");
        }
        return getUserOrThrow(userId).getBalanceFen();
    }

    /**
     * 写入一条余额变动流水记录。
     *
     * @param userId 用户ID
     * @param type 流水类型
     * @param amountFen 变动金额
     * @param balanceAfter 变动后余额（单位：分）
     * @param orderId 关联业务ID
     * @param payMethod 支付方式
     * @param remark 备注
     */
    private void saveLog(Long userId, String type, long amountFen, long balanceAfter,
                         Long orderId, String payMethod, String remark) {
        BalanceLog log = new BalanceLog();
        log.setUserId(userId);
        log.setType(type);
        log.setAmountFen(amountFen);
        log.setBalanceAfterFen(balanceAfter);
        log.setOrderId(orderId);
        log.setPayMethod(payMethod);
        log.setRemark(remark);
        balanceLogMapper.insert(log);
    }

    /**
     * 按ID查询用户，不存在则抛出异常。
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    private User getUserOrThrow(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /**
     * 校验并规范化充值支付方式，仅支持微信或支付宝。
     *
     * @param payMethod 支付方式
     * @return 规范化后的支付方式
     */
    private String normalizeRechargePayMethod(String payMethod) {
        if (!StringUtils.hasText(payMethod)) {
            throw new BusinessException("请选择支付方式");
        }
        return switch (payMethod.trim().toLowerCase()) {
            case "wechat", "alipay" -> payMethod.trim().toLowerCase();
            default -> throw new BusinessException("充值仅支持微信或支付宝");
        };
    }
}
