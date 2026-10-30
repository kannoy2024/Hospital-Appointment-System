package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.vo.BalanceLogVO;
import com.system.dto_vo.vo.PaymentPrepayVO;
import com.system.dto_vo.dto.WalletRechargeRequest;

/**
 * 钱包服务：余额充值、支付扣款、退款、流水查询。
 * 余额扣减均使用乐观锁（WHERE balance >= amount）防止并发透支。
 */
public interface WalletService {

    /** 发起充值预支付：只建支付单，余额此时不变，到账要等支付回调 */
    PaymentPrepayVO prepayRecharge(Long userId, WalletRechargeRequest request);

    /** 充值到账（仅由支付回调调用）：幂等，同一支付单只入账一次 */
    void applyRecharge(Long userId, Long paymentOrderId, long amountFen, String payMethod);

    /** 余额支付挂号费（预约支付时调用） */
    void payByBalance(Long userId, Long appointmentId, long amountFen);

    /** 余额支付处方药费 */
    void payForPrescription(Long userId, Long prescriptionId, long amountFen);

    /** 取消预约时退款（仅余额支付的退回余额） */
    void refundForAppointment(Long userId, Long appointmentId, long amountFen);

    /** 分页查询当前用户的余额流水 */
    PageResult<BalanceLogVO> pageMyLogs(Long userId, long current, long size);
}
