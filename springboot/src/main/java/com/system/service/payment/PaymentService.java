package com.system.service.payment;

import com.system.dto_vo.vo.PaymentOrderVO;
import com.system.dto_vo.vo.RefundOrderVO;
import com.system.entity.PaymentOrder;
import com.system.entity.RefundOrder;
import com.system.service.payment.model.PayNotifyPayload;
import com.system.service.payment.model.PrepayCommand;
import com.system.service.payment.model.PrepayResult;

/**
 * 支付门面：业务层只跟它打交道，回答“这单多少钱、付完通知我”。
 * 具体怎么收钱由 PaymentGateway 的实现决定（S1 = Mock，S5 = 微信）。
 */
public interface PaymentService {

    /**
     * 下单（可重入）：
     * 同一 (bizType, bizId) 若已存在未过期的 CREATED 支付单，直接复用那条，不新建，
     * 避免同一业务单出现两条可支付单、被收两次钱。
     */
    PrepayResult prepay(PrepayCommand command);

    /**
     * 处理支付回调（幂等）。
     * 幂等靠 CAS 更新（WHERE status='CREATED'）而不是“先查后改”。
     *
     * @return SUCCESS / DUP / REFUND / ERROR，对应 payment_notify_log.process_result
     */
    String handlePayNotify(PayNotifyPayload payload);

    /**
     * 处理退款回调（幂等）。
     *
     * <p>与支付回调的关键差异：网关告知「退款失败」时本端已正确记账，
     * 必须返回 SUCCESS（不能让网关一直重试），只在 process_result 里记 REFUND_FAILED。
     *
     * @return SUCCESS / DUP / REFUND_FAILED / ERROR
     */
    String handleRefundNotify(PayNotifyPayload payload);

    /**
     * 关单：先在网关关单，再把支付单 CAS 从 CREATED 改成 CLOSED。
     * 顺序不可颠倒，否则会出现“号已释放、支付单还活着”的窗口。
     *
     * @return 是否真的关闭了（false = 不是 CREATED 状态或单不存在）
     */
    boolean closeIfCreated(String outTradeNo);

    /**
     * 查某个业务单当前还“活着”的支付单号（status=CREATED 里最新的一条）。
     *
     * <p>业务侧在取消预约/超时清理前用它找到“需要先关掉的那张单”，
     * 把「先关单、再改业务」这条铁律落到调用点上。
     *
     * @param bizType 业务类型，如 {@link PaymentOrder#BIZ_APPOINTMENT}
     * @param bizId   业务单 id
     * @return 没有 CREATED 支付单时返回 null
     */
    String findCreatedOutTradeNo(String bizType, Long bizId);

    /**
     * 查某个业务单已收款（status=PAID）的支付单号（最新的一条）。
     *
     * <p>取消已支付预约时用它找到“该退哪一笔”，退款由本接口的 {@link #refund} 完成。
     *
     * @return 没有 PAID 支付单时返回 null
     */
    String findPaidOutTradeNo(String bizType, Long bizId);

    /**
     * 发起全额退款（幂等）。
     *
     * <p>顺序与关单相反，是「先本地、后网关」：
     * CAS PAID→REFUNDING → 落 refund_order(PROCESSING) → 才调网关。
     * 因为一旦先调网关再写库，进程崩在中间就会产生一笔本地完全没有记录的退款，对账查不到、也无法重试。
     *
     * <p>网关调用失败**不回滚**：支付单保持 REFUNDING、退款单保持 PROCESSING，交给对账用同一
     * out_refund_no 重提。
     *
     * @param outTradeNo 原支付单号
     * @param reason     退款原因（写 refund_order.reason）
     * @return 退款单视图（重复调用返回已存在的那条，不会重复退款）
     */
    RefundOrderVO refund(String outTradeNo, String reason);

    /**
     * 重试一张卡住的退款（对账专用）。
     *
     * <p>与 {@link #refund} 的区别：refund 遇到「已有退款单」会直接返回（幂等），
     * 而本方法专门处理「退款单卡在 PROCESSING」这一种情况：
     * <ul>
     *   <li>refund_id 为空 → 说明进程崩在提交网关之前，用同一 out_refund_no 重提；</li>
     *   <li>refund_id 有值 → 先向网关查询结果，绝不盲目重提。</li>
     * </ul>
     *
     * @param outRefundNo 商户退款单号
     * @return true 表示本次调用完成了状态收敛（成功或失败）
     */
    boolean retryRefund(String outRefundNo);

    /** 查退款单视图（前端轮询用） */
    RefundOrderVO getRefundByOutTradeNo(String outTradeNo);

    /** 查退款单实体（Mock 收银台组装退款回调用） */
    RefundOrder getRefundEntityByOutTradeNo(String outTradeNo);

    /**
     * 对账兜底：扫「支付单仍是 PAID、但业务单已经取消」的记录并发起补救退款。
     *
     * <p>覆盖的场景：用户取消预约时 {@link #refund} 抛异常被业务侧吞掉，
     * 结果预约已 cancelled、钱却仍挂在我们账上。
     *
     * @param batchSize 单次扫描上限
     * @return 本轮实际发起退款的条数
     */
    int sweepPaidButBizCancelled(int batchSize);

    /** 查询支付单实体（越权校验、内部调用用） */
    PaymentOrder getEntityByOutTradeNo(String outTradeNo);

    /** 查询支付单视图（前端轮询用） */
    PaymentOrderVO getByOutTradeNo(String outTradeNo);
}
