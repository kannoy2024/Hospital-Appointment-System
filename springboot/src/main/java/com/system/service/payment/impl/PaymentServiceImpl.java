package com.system.service.payment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.system.common.BusinessException;
import com.system.common.MoneyUtils;
import com.system.config.WeChatPayProperties;
import com.system.dto_vo.vo.PaymentOrderVO;
import com.system.dto_vo.vo.RefundOrderVO;
import com.system.entity.PaymentNotifyLog;
import com.system.entity.PaymentOrder;
import com.system.entity.RefundOrder;
import com.system.mapper.PaymentNotifyLogMapper;
import com.system.mapper.PaymentOrderMapper;
import com.system.mapper.RefundOrderMapper;
import com.system.service.NotificationService;
import com.system.service.payment.PaymentService;
import com.system.service.payment.handler.PaymentBizHandler;
import com.system.service.payment.gateway.PaymentGateway;
import com.system.service.payment.model.PayNotifyPayload;
import com.system.service.payment.model.PrepayCommand;
import com.system.service.payment.model.PrepayResult;
import com.system.service.payment.model.RefundCommand;
import com.system.service.payment.model.RefundResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 支付门面实现。四条铁律在这里落地：
 * <ol>
 *   <li>回调幂等靠 CAS（WHERE status='CREATED' / 'PROCESSING'），绝不“先查后改”；</li>
 *   <li>关单「先网关、后本地」，退款「先本地、后网关」—— 两者顺序相反，理由见 {@link #refund}；</li>
 *   <li>回调原文一律先落 payment_notify_log，验签失败也落；</li>
 *   <li>已 CLOSED 却收到成功回调，必须自动退款，绝不静默丢弃。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter TRADE_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ALNUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** refund_order.out_refund_no 的后缀：out_trade_no 25 位 + 2 位 = 27，仍在网关 32 位上界内 */
    private static final String REFUND_NO_SUFFIX = "-R";

    /** refund_order.reason 是 varchar(200) */
    private static final int REASON_MAX_LENGTH = 200;

    /** 同一笔支付单最多自动重试几次退款；超过就停手报警，交给人工 */
    private static final int MAX_REFUND_RETRY = 3;

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final PaymentGateway paymentGateway;
    private final WeChatPayProperties payProperties;
    private final NotificationService notificationService;

    /** Spring 自动收集全部实现；S1 为空集合，S2/S3 接入后自然出现 */
    private final List<PaymentBizHandler> bizHandlers;

    private final SecureRandom random = new SecureRandom();

    /** bizType → handler，启动时构建一次 */
    private Map<String, PaymentBizHandler> handlerMap = Map.of();

    @PostConstruct
    void initHandlers() {
        handlerMap = bizHandlers.stream().collect(Collectors.toMap(
                PaymentBizHandler::bizType,
                h -> h,
                (a, b) -> {
                    // 同一业务类型出现两个 handler 是配置错误，直接启动失败，避免“到底谁收钱”的歧义
                    throw new IllegalStateException("存在重复的 PaymentBizHandler：bizType=" + a.bizType());
                }));
        log.info("[Pay] 已装配 PaymentBizHandler {} 个：{}", handlerMap.size(), handlerMap.keySet());
    }

    // ==================== 下单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepayResult prepay(PrepayCommand command) {
        if (command == null || !StringUtils.hasText(command.getBizType()) || command.getBizId() == null) {
            throw new BusinessException("下单参数不完整：bizType / bizId 必填");
        }
        if (command.getAmountFen() <= 0) {
            throw new BusinessException("支付金额必须大于 0 分");
        }

        // ★可重入护栏：同一业务单只要还有未过期的 CREATED 单，就复用它。
        //   充值（RECHARGE）没有业务表、bizId 只能填 userId，若也复用，用户连充两笔会被合并成一笔，
        //   因此由调用方显式关闭（PrepayCommand#reuseExistingCreated）。
        if (command.isReuseExistingCreated()) {
            PaymentOrder reusable = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getBizType, command.getBizType())
                    .eq(PaymentOrder::getBizId, command.getBizId())
                    .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_CREATED)
                    .gt(PaymentOrder::getExpireTime, LocalDateTime.now())
                    .orderByDesc(PaymentOrder::getId)
                    .last("LIMIT 1"));
            if (reusable != null) {
                log.info("[Pay] 复用未过期的支付单 outTradeNo={} bizType={} bizId={}",
                        reusable.getOutTradeNo(), reusable.getBizType(), reusable.getBizId());
                command.setOutTradeNo(reusable.getOutTradeNo());
                PrepayResult cached = new PrepayResult();
                cached.setPrepayId(reusable.getPrepayId());
                cached.setOutTradeNo(reusable.getOutTradeNo());
                cached.setPayParams(cashierParams(reusable.getOutTradeNo()));
                return cached;
            }
        }

        // 本地先生成单号并落一条 CREATED（这样收银台/回调在网关调用前后都能查到单）
        String outTradeNo = generateOutTradeNo(command.getBizType());
        command.setOutTradeNo(outTradeNo);

        PaymentOrder order = new PaymentOrder();
        order.setOutTradeNo(outTradeNo);
        order.setBizType(command.getBizType());
        order.setBizId(command.getBizId());
        order.setUserId(command.getUserId());
        order.setAmountFen(command.getAmountFen());
        order.setCurrency("CNY");
        order.setPayMethod(StringUtils.hasText(command.getPayMethod()) ? command.getPayMethod() : "wechat");
        order.setChannel(paymentGateway.channel());
        order.setStatus(PaymentOrder.STATUS_CREATED);
        order.setDescription(command.getDescription());
        order.setPayerOpenid(command.getPayerOpenid());
        // 过期时间与 app.appointment.unpaid-expire-minutes 同源：调用方算好传进来最准，没传按配置兜底
        order.setExpireTime(command.getExpireTime() != null
                ? command.getExpireTime()
                : LocalDateTime.now().plusMinutes(payProperties.getMock().getPrepayExpireMinutes()));
        paymentOrderMapper.insert(order);

        PrepayResult result = paymentGateway.prepay(command);
        order.setPrepayId(result.getPrepayId());
        paymentOrderMapper.updateById(order);

        if (result.getOutTradeNo() == null) {
            result.setOutTradeNo(outTradeNo);
        }
        if (result.getPayParams() == null) {
            result.setPayParams(cashierParams(outTradeNo));
        }
        return result;
    }

    // ==================== 支付回调 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handlePayNotify(PayNotifyPayload payload) {
        // ① 无论验签是否通过，原文一律先落库：幂等基石 + 线上排障的唯一凭据
        PaymentNotifyLog logRow = new PaymentNotifyLog();
        logRow.setChannel(paymentGateway.channel());
        logRow.setNotifyType(StringUtils.hasText(payload.getNotifyType())
                ? payload.getNotifyType() : PaymentNotifyLog.TYPE_PAY);
        logRow.setOutTradeNo(payload.getOutTradeNo());
        logRow.setOutRefundNo(payload.getOutRefundNo());
        logRow.setTransactionId(payload.getTransactionId());
        logRow.setRawBody(payload.getRawBody());
        logRow.setSignVerified(payload.isSignVerified() ? 1 : 0);

        if (!payload.isSignVerified()) {
            logRow.setProcessResult(PaymentNotifyLog.RESULT_ERROR);
            logRow.setErrorMsg(payload.getErrorMsg());
            paymentNotifyLogMapper.insert(logRow);
            log.error("[Pay] 回调验签失败，已落库但不改业务状态：{}", payload.getErrorMsg());
            return PaymentNotifyLog.RESULT_ERROR;
        }

        if (!payload.isSuccess()) {
            logRow.setProcessResult(PaymentNotifyLog.RESULT_ERROR);
            logRow.setErrorMsg("网关回调标记为失败");
            paymentNotifyLogMapper.insert(logRow);
            log.warn("[Pay] 网关回调标记为失败 outTradeNo={}", payload.getOutTradeNo());
            return PaymentNotifyLog.RESULT_ERROR;
        }

        PaymentOrder order = getEntityByOutTradeNo(payload.getOutTradeNo());
        if (order == null) {
            logRow.setProcessResult(PaymentNotifyLog.RESULT_ERROR);
            logRow.setErrorMsg("支付单不存在：" + payload.getOutTradeNo());
            paymentNotifyLogMapper.insert(logRow);
            log.error("[Pay] 回调找不到支付单：{}", payload.getOutTradeNo());
            return PaymentNotifyLog.RESULT_ERROR;
        }

        // 金额必须一致，否则宁可拒绝也不能误认（真实资损防线）
        if (order.getAmountFen() != payload.getAmountFen()) {
            logRow.setProcessResult(PaymentNotifyLog.RESULT_ERROR);
            logRow.setErrorMsg("金额不一致：单=" + order.getAmountFen() + " 回调=" + payload.getAmountFen());
            paymentNotifyLogMapper.insert(logRow);
            log.error("[Pay] 回调金额不一致 outTradeNo={} 单={} 回调={}",
                    payload.getOutTradeNo(), order.getAmountFen(), payload.getAmountFen());
            return PaymentNotifyLog.RESULT_ERROR;
        }

        // ② CAS：affectedRows==0 说明已被处理过（或已关闭）→ DUP
        int affected = paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId())
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_CREATED)
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_PAID)
                .set(PaymentOrder::getTransactionId, payload.getTransactionId())
                .set(PaymentOrder::getPaidTime, LocalDateTime.now()));
        if (affected == 0) {
            // ★S4 核心特例：单已 CLOSED 却收到成功回调 = 用户第 29 分钟付成功了，钱真的到账了。
            //   绝不能静默丢弃，必须自动发起退款（这是唯一能让「先关单后回调」不丢钱的处理方式）。
            if (PaymentOrder.STATUS_CLOSED.equals(order.getStatus())) {
                logRow.setProcessResult(PaymentNotifyLog.RESULT_REFUND);
                logRow.setErrorMsg("支付单已关闭却收到成功回调，已自动发起退款");
                paymentNotifyLogMapper.insert(logRow);
                log.error("[Pay] ★异常：支付单已 CLOSED 却收到成功回调，自动发起退款 outTradeNo={}",
                        payload.getOutTradeNo());
                try {
                    doRefund(order, "支付单已关闭后的成功回调（第 29 分钟竞态）");
                } catch (Exception e) {
                    // 退款发起失败也不能让回调变成“未处理”：退款单已留 PROCESSING / 支付单留 REFUNDING，
                    // 由 PaymentReconcileService 的重试兜底。
                    log.error("[Pay] 自动退款发起失败，已交由对账任务处理 outTradeNo={}",
                            payload.getOutTradeNo(), e);
                }
                return PaymentNotifyLog.RESULT_REFUND;
            }
            logRow.setProcessResult(PaymentNotifyLog.RESULT_DUP);
            logRow.setErrorMsg("重复回调，支付单当前状态：" + order.getStatus());
            paymentNotifyLogMapper.insert(logRow);
            log.warn("[Pay] 重复回调 outTradeNo={} 当前状态={}", payload.getOutTradeNo(), order.getStatus());
            return PaymentNotifyLog.RESULT_DUP;
        }

        // ③ 业务侧推进：handler 是业务状态机的唯一入口
        PaymentBizHandler handler = handlerMap.get(order.getBizType());
        if (handler != null) {
            handler.markPaid(order, payload);
        } else {
            log.warn("[Pay] 尚未接入业务 handler，bizType={} bizId={}，仅支付单已置 PAID",
                    order.getBizType(), order.getBizId());
        }

        logRow.setProcessResult(PaymentNotifyLog.RESULT_SUCCESS);
        paymentNotifyLogMapper.insert(logRow);
        log.info("[Pay] 回调处理成功 outTradeNo={} transactionId={}",
                payload.getOutTradeNo(), payload.getTransactionId());
        return PaymentNotifyLog.RESULT_SUCCESS;
    }

    // ==================== 退款回调 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleRefundNotify(PayNotifyPayload payload) {
        PaymentNotifyLog logRow = new PaymentNotifyLog();
        logRow.setChannel(paymentGateway.channel());
        logRow.setNotifyType(PaymentNotifyLog.TYPE_REFUND);
        logRow.setOutTradeNo(payload.getOutTradeNo());
        logRow.setOutRefundNo(payload.getOutRefundNo());
        logRow.setTransactionId(payload.getTransactionId());
        logRow.setRawBody(payload.getRawBody());
        logRow.setSignVerified(payload.isSignVerified() ? 1 : 0);

        if (!payload.isSignVerified()) {
            return rejectRefund(logRow, "退款回调验签失败：" + payload.getErrorMsg());
        }
        if (!StringUtils.hasText(payload.getOutRefundNo())) {
            return rejectRefund(logRow, "退款回调缺少 out_refund_no");
        }

        RefundOrder refund = selectRefundByOutRefundNo(payload.getOutRefundNo());
        if (refund == null) {
            return rejectRefund(logRow, "退款单不存在：" + payload.getOutRefundNo());
        }
        PaymentOrder order = getEntityByOutTradeNo(refund.getOutTradeNo());
        if (order == null) {
            return rejectRefund(logRow, "退款单关联的支付单不存在：" + refund.getOutTradeNo());
        }

        if (!payload.isSuccess()) {
            // 网关告知「退款失败」：本端已正确记账，绝不能返回 ERROR 让网关再重试 8 次
            boolean applied = convergeRefundFailed(refund, order, "网关回调标记退款失败");
            logRow.setProcessResult(PaymentNotifyLog.RESULT_REFUND_FAILED);
            logRow.setErrorMsg(applied
                    ? "网关回调标记退款失败，支付单已退回 PAID 等重试"
                    : "退款单已收敛，忽略重复回调");
            paymentNotifyLogMapper.insert(logRow);
            return PaymentNotifyLog.RESULT_REFUND_FAILED;
        }

        if (!convergeRefundSuccess(refund, order)) {
            logRow.setProcessResult(PaymentNotifyLog.RESULT_DUP);
            logRow.setErrorMsg("退款单已收敛，忽略重复回调（当前状态：" + refund.getStatus() + "）");
            paymentNotifyLogMapper.insert(logRow);
            log.warn("[Refund] 重复退款回调 outRefundNo={} 当前状态={}",
                    payload.getOutRefundNo(), refund.getStatus());
            return PaymentNotifyLog.RESULT_DUP;
        }

        logRow.setProcessResult(PaymentNotifyLog.RESULT_SUCCESS);
        paymentNotifyLogMapper.insert(logRow);
        log.info("[Refund] 退款回调处理成功 outRefundNo={}", payload.getOutRefundNo());
        return PaymentNotifyLog.RESULT_SUCCESS;
    }

    // ==================== 关单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeIfCreated(String outTradeNo) {
        PaymentOrder order = getEntityByOutTradeNo(outTradeNo);
        if (order == null || !PaymentOrder.STATUS_CREATED.equals(order.getStatus())) {
            return false;
        }
        // ★顺序铁律：先网关关单，再改本地状态
        //
        // 网关关单失败（网络抖动 / 单已支付）一律吞掉并返回 false：
        // 1) 此时本地状态没动，返回 false 是诚实的——调用方该走自己的回滚分支；
        // 2) closeIfCreated 常被余额支付、取消预约等业务事务复用，异常若冒出去会把外层事务
        //    打成 rollback-only（即便调用方 catch 也救不回来），导致「余额已扣成功却提示失败」。
        try {
            paymentGateway.close(outTradeNo);
        } catch (Exception e) {
            log.error("[Pay] 网关关单失败，保留 CREATED 等待对账兜底 outTradeNo={}", outTradeNo, e);
            return false;
        }

        int affected = paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId())
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_CREATED)
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_CLOSED)
                .set(PaymentOrder::getCloseTime, LocalDateTime.now()));
        if (affected == 0) {
            // 网关关单与本地 CAS 之间被回调抢先（用户恰好付成功了），交由回调路径处理
            log.warn("[Pay] 关单 CAS 未命中，可能已被回调抢先 outTradeNo={}", outTradeNo);
            return false;
        }

        PaymentBizHandler handler = handlerMap.get(order.getBizType());
        if (handler != null) {
            handler.onPayClosed(order);
        }
        log.info("[Pay] 支付单已关闭 outTradeNo={}", outTradeNo);
        return true;
    }

    // ==================== 退款 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundOrderVO refund(String outTradeNo, String reason) {
        if (!StringUtils.hasText(outTradeNo)) {
            throw new BusinessException("退款失败：支付单号为空");
        }
        PaymentOrder order = getEntityByOutTradeNo(outTradeNo);
        if (order == null) {
            throw new BusinessException("退款失败：支付单不存在 " + outTradeNo);
        }
        if (PaymentOrder.BIZ_RECHARGE.equals(order.getBizType())) {
            // 风控：充值退款是高危操作，本项目刻意不开口子（真实业务需人工审核走银行渠道）
            throw new BusinessException("充值订单不支持自动退款");
        }
        return RefundOrderVO.fromEntity(doRefund(order, reason));
    }

    /**
     * 退款主流程（幂等三要素：支付单 CAS + out_refund_no 唯一 + 复用已有退款单）。
     *
     * <p>★顺序与关单相反，是「先本地、后网关」：
     * <ol>
     *   <li>CAS 把支付单从 PAID/CLOSED 推到 REFUNDING —— 挡住并发，也挡住重复退款；</li>
     *   <li>落 refund_order(PROCESSING) —— 先让“我们打算退这笔钱”有记录；</li>
     *   <li>最后才调网关。</li>
     * </ol>
     * 反过来（先调网关再写库）一旦进程崩在中间，就会产生一笔本地完全没有记录的退款：
     * 对账查不到、也无法重试，那才是真正的资损。
     *
     * <p>网关调用抛异常时**不回滚**：支付单留 REFUNDING、退款单留 PROCESSING，
     * 由 {@code PaymentReconcileService} 用同一 out_refund_no 重提（真实微信按此单号幂等）。
     */
    private RefundOrder doRefund(PaymentOrder order, String reason) {
        // ① 幂等第一层：已有退款单且不是 FAILED → 原样返回，绝不退第二次
        RefundOrder existing = selectRefundByOutTradeNo(order.getOutTradeNo());
        if (existing != null && !RefundOrder.STATUS_FAILED.equals(existing.getStatus())) {
            log.info("[Refund] 复用已有退款单 outRefundNo={} status={}",
                    existing.getOutRefundNo(), existing.getStatus());
            return existing;
        }

        // ② 幂等第二层：CAS。CLOSED 是唯一允许的异常跃迁（先关单后回调，钱已到账必须退）
        boolean fromClosed = PaymentOrder.STATUS_CLOSED.equals(order.getStatus());
        int affected = paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId())
                .in(PaymentOrder::getStatus, PaymentOrder.STATUS_PAID, PaymentOrder.STATUS_CLOSED)
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_REFUNDING));
        if (affected == 0) {
            // 被别的线程抢先，或状态是 CREATED / REFUNDED / REFUNDING
            RefundOrder again = selectRefundByOutTradeNo(order.getOutTradeNo());
            if (again != null) {
                return again;
            }
            throw new BusinessException("支付单当前状态不可退款：" + order.getStatus());
        }
        if (fromClosed) {
            log.error("[Refund] ★异常跃迁：CLOSED 的支付单收到成功回调，强制进入退款 outTradeNo={}",
                    order.getOutTradeNo());
        }

        // ③ 落退款单（PROCESSING）。FAILED 的旧行复用，避免撞 out_refund_no 唯一键
        RefundOrder refund;
        if (existing != null) {
            refund = existing;
            refund.setStatus(RefundOrder.STATUS_PROCESSING);
            refund.setReason(truncateReason(reason));
            refund.setRefundId(null);
            refundOrderMapper.updateById(refund);
            log.info("[Refund] 重试失败过的退款单 outRefundNo={}（沿用同一单号重提网关）",
                    refund.getOutRefundNo());
        } else {
            refund = new RefundOrder();
            refund.setOutRefundNo(buildOutRefundNo(order.getOutTradeNo()));
            refund.setOutTradeNo(order.getOutTradeNo());
            refund.setPaymentOrderId(order.getId());
            refund.setUserId(order.getUserId());
            refund.setAmountFen(order.getAmountFen());
            refund.setStatus(RefundOrder.STATUS_PROCESSING);
            refund.setReason(truncateReason(reason));
            refundOrderMapper.insert(refund);
        }

        // ④ 调网关。抛异常不回滚，留给对账
        try {
            RefundResult result = paymentGateway.refund(toRefundCommand(order, refund));
            if (StringUtils.hasText(result.getRefundId())) {
                refund.setRefundId(result.getRefundId());
                refundOrderMapper.updateById(refund);
            }
            // 网关“受理即成功”的通道（同步退款）可以直接收敛；Mock 返回 PROCESSING 时走 refund_notify
            if (result.isSuccess() && RefundOrder.STATUS_SUCCESS.equals(result.getState())) {
                convergeRefundSuccess(refund, order);
                refund.setStatus(RefundOrder.STATUS_SUCCESS);
                refund.setSuccessTime(LocalDateTime.now());
            }
        } catch (Exception e) {
            log.error("[Refund] 网关退款调用失败，保留 PROCESSING 等对账重试 outRefundNo={}",
                    refund.getOutRefundNo(), e);
        }
        return refund;
    }

    /**
     * 退款成功收敛：refund_order PROCESSING→SUCCESS + payment_order REFUNDING→REFUNDED + 通知用户。
     *
     * @return true = 本次调用真正完成了收敛；false = 已被处理过（重复回调）
     */
    private boolean convergeRefundSuccess(RefundOrder refund, PaymentOrder order) {
        int affected = refundOrderMapper.update(null, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getId, refund.getId())
                .eq(RefundOrder::getStatus, RefundOrder.STATUS_PROCESSING)
                .set(RefundOrder::getStatus, RefundOrder.STATUS_SUCCESS)
                .set(RefundOrder::getSuccessTime, LocalDateTime.now()));
        if (affected == 0) {
            if (RefundOrder.STATUS_FAILED.equals(refund.getStatus())) {
                log.error("[Refund] ★退款单[{}] 已标记 FAILED，却又收到成功回调，请人工核对账实",
                        refund.getOutRefundNo());
            }
            return false;
        }

        paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId())
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_REFUNDING)
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_REFUNDED));

        notifyRefund(order, true);
        log.info("[Refund] 退款成功 outRefundNo={} amountFen={} → payment_order REFUNDED",
                refund.getOutRefundNo(), refund.getAmountFen());
        return true;
    }

    /**
     * 退款失败收敛：refund_order PROCESSING→FAILED + payment_order REFUNDING→**回退 PAID** + 告警。
     *
     * <p>为什么退回 PAID 而不是停在 REFUNDING：PAID 是「可以再次 CAS 到 REFUNDING」的起点，
     * 停住就等于把这张单永久卡死；PAID 的语义也最诚实 —— 钱还在我们手里。
     *
     * @return true = 本次调用真正完成了收敛；false = 已被处理过
     */
    private boolean convergeRefundFailed(RefundOrder refund, PaymentOrder order, String errorMsg) {
        int affected = refundOrderMapper.update(null, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getId, refund.getId())
                .eq(RefundOrder::getStatus, RefundOrder.STATUS_PROCESSING)
                .set(RefundOrder::getStatus, RefundOrder.STATUS_FAILED));
        if (affected == 0) {
            return false;
        }

        paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId())
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_REFUNDING)
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_PAID));

        log.error("[Refund] ★退款失败，需人工介入 outRefundNo={} outTradeNo={} reason={}",
                refund.getOutRefundNo(), refund.getOutTradeNo(), errorMsg);
        notifyRefund(order, false);
        return true;
    }

    /**
     * 重试卡住的退款（对账专用）。
     *
     * <p>两段式判断，绝不对“已提交过”的退款盲目重提：
     * refund_id 为空说明崩在提交网关之前（可以安全重提，网关按 out_refund_no 幂等）；
     * 有 refund_id 说明网关已受理，只能问结果。
     */
    @Override
    public boolean retryRefund(String outRefundNo) {
        RefundOrder refund = selectRefundByOutRefundNo(outRefundNo);
        if (refund == null || !RefundOrder.STATUS_PROCESSING.equals(refund.getStatus())) {
            return false;
        }
        PaymentOrder order = getEntityByOutTradeNo(refund.getOutTradeNo());
        if (order == null) {
            log.error("[Refund] 重试退款时支付单不存在 outRefundNo={} outTradeNo={}",
                    outRefundNo, refund.getOutTradeNo());
            return false;
        }

        if (!StringUtils.hasText(refund.getRefundId())) {
            log.warn("[Refund] 退款未提交到网关，重新提交 outRefundNo={}", outRefundNo);
            try {
                RefundResult result = paymentGateway.refund(toRefundCommand(order, refund));
                if (StringUtils.hasText(result.getRefundId())) {
                    refund.setRefundId(result.getRefundId());
                    refundOrderMapper.updateById(refund);
                }
                if (result.isSuccess() && RefundOrder.STATUS_SUCCESS.equals(result.getState())) {
                    return convergeRefundSuccess(refund, order);
                }
            } catch (Exception e) {
                log.error("[Refund] 重新提交网关失败 outRefundNo={}", outRefundNo, e);
            }
            return false;
        }

        RefundResult result = paymentGateway.queryRefund(outRefundNo);
        if (result.isSuccess()) {
            return convergeRefundSuccess(refund, order);
        }
        if (RefundOrder.STATUS_FAILED.equals(result.getState())) {
            return convergeRefundFailed(refund, order, "对账查询到网关退款失败");
        }
        // PROCESSING / NOTFOUND：还没结论，下一轮再问
        return false;
    }

    // ==================== 查询 ====================

    @Override
    public String findCreatedOutTradeNo(String bizType, Long bizId) {
        if (!StringUtils.hasText(bizType) || bizId == null) {
            return null;
        }
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getBizType, bizType)
                .eq(PaymentOrder::getBizId, bizId)
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_CREATED)
                .orderByDesc(PaymentOrder::getId)
                .last("LIMIT 1"));
        return order == null ? null : order.getOutTradeNo();
    }

    @Override
    public String findPaidOutTradeNo(String bizType, Long bizId) {
        if (!StringUtils.hasText(bizType) || bizId == null) {
            return null;
        }
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getBizType, bizType)
                .eq(PaymentOrder::getBizId, bizId)
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_PAID)
                .orderByDesc(PaymentOrder::getId)
                .last("LIMIT 1"));
        return order == null ? null : order.getOutTradeNo();
    }

    @Override
    public RefundOrderVO getRefundByOutTradeNo(String outTradeNo) {
        return RefundOrderVO.fromEntity(selectRefundByOutTradeNo(outTradeNo));
    }

    @Override
    public RefundOrder getRefundEntityByOutTradeNo(String outTradeNo) {
        return selectRefundByOutTradeNo(outTradeNo);
    }

    @Override
    public PaymentOrder getEntityByOutTradeNo(String outTradeNo) {
        if (!StringUtils.hasText(outTradeNo)) {
            return null;
        }
        return paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOutTradeNo, outTradeNo));
    }

    @Override
    public PaymentOrderVO getByOutTradeNo(String outTradeNo) {
        return PaymentOrderVO.fromEntity(getEntityByOutTradeNo(outTradeNo));
    }

    // ==================== 对账兜底 ====================

    @Override
    public int sweepPaidButBizCancelled(int batchSize) {
        if (handlerMap.isEmpty()) {
            return 0;
        }
        List<PaymentOrder> paid = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_PAID)
                .in(PaymentOrder::getBizType, handlerMap.keySet())
                .orderByDesc(PaymentOrder::getId)
                .last("LIMIT " + batchSize));
        if (paid.isEmpty()) {
            return 0;
        }

        int refunded = 0;
        for (PaymentOrder order : paid) {
            PaymentBizHandler handler = handlerMap.get(order.getBizType());
            if (handler == null || !handler.isBizCancelled(order)) {
                continue;
            }
            // 已有退款单就交给它自己收敛（回调/对账），避免重复退款；
            // 但 FAILED 的允许重试——否则「回 PAID 等重试」就成了永不重试。
            // 同时必须封顶：宁可停下来报警，也不能变成每分钟一次的无限重试。
            RefundOrder existing = selectRefundByOutTradeNo(order.getOutTradeNo());
            if (existing != null) {
                if (!RefundOrder.STATUS_FAILED.equals(existing.getStatus())) {
                    continue;
                }
                long failedAttempts = countRefundFailures(order.getOutTradeNo());
                if (failedAttempts >= MAX_REFUND_RETRY) {
                    log.error("[Reconcile] ★退款已失败 {} 次，停止自动重试，请人工介入 outTradeNo={}",
                            failedAttempts, order.getOutTradeNo());
                    continue;
                }
                log.warn("[Reconcile] 重试失败过的退款 outTradeNo={}（第 {} 次重试）",
                        order.getOutTradeNo(), failedAttempts + 1);
            }
            try {
                doRefund(order, "业务单已取消，对账补救退款");
                refunded++;
                log.warn("[Reconcile] ★补救退款：支付单 PAID 但业务单已取消 outTradeNo={} bizType={} bizId={}",
                        order.getOutTradeNo(), order.getBizType(), order.getBizId());
            } catch (Exception e) {
                log.error("[Reconcile] 补救退款发起失败 outTradeNo={}", order.getOutTradeNo(), e);
            }
        }
        return refunded;
    }

    // ==================== 内部工具 ====================

    /**
     * 统计某个支付单「退款失败」的次数。
     *
     * <p>退款单是复用同一行的（status 在 PROCESSING/FAILED 之间翻转），没有独立的尝试计数列，
     * 所以借 payment_notify_log 里 process_result='REFUND_FAILED' 的记录来数——零 DDL，且天然可审计。
     */
    private long countRefundFailures(String outTradeNo) {
        return paymentNotifyLogMapper.selectCount(new LambdaQueryWrapper<PaymentNotifyLog>()
                .eq(PaymentNotifyLog::getOutTradeNo, outTradeNo)
                .eq(PaymentNotifyLog::getProcessResult, PaymentNotifyLog.RESULT_REFUND_FAILED));
    }

    /**
     * 生成 out_trade_no：业务前缀 + yyyyMMddHHmmss + 8 位随机，长度恒为 25。
     * 字符集只用数字与字母，满足“数字/字母/_/-/|/* 且 6~32”的网关约束。
     */
    static String generateOutTradeNo(String bizType) {
        String prefix = switch (bizType) {
            case PaymentOrder.BIZ_APPOINTMENT -> "APPT";
            case PaymentOrder.BIZ_PRESCRIPTION -> "PRESC";
            case PaymentOrder.BIZ_RECHARGE -> "RCHG";
            default -> throw new BusinessException("未知业务类型：" + bizType);
        };
        return prefix + LocalDateTime.now().format(TRADE_NO_FMT) + randomSuffix(8);
    }

    private static String randomSuffix(int length) {
        SecureRandom sr = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALNUM.charAt(sr.nextInt(ALNUM.length())));
        }
        return sb.toString();
    }

    /** Mock 阶段前端拿到的“支付参数”就是收银台地址 */
    private Map<String, String> cashierParams(String outTradeNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("outTradeNo", outTradeNo);
        params.put("cashierUrl", payProperties.getMock().getBaseUrl() + "/mock/pay/" + outTradeNo);
        return params;
    }

    /** 便于将来在别处读取 handler（例如退款时按 bizType 找业务） */
    Optional<PaymentBizHandler> findHandler(String bizType) {
        return Optional.ofNullable(handlerMap.get(bizType));
    }

    private RefundOrder selectRefundByOutTradeNo(String outTradeNo) {
        if (!StringUtils.hasText(outTradeNo)) {
            return null;
        }
        return refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrder>()
                .eq(RefundOrder::getOutTradeNo, outTradeNo)
                .orderByDesc(RefundOrder::getId)
                .last("LIMIT 1"));
    }

    private RefundOrder selectRefundByOutRefundNo(String outRefundNo) {
        return refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrder>()
                .eq(RefundOrder::getOutRefundNo, outRefundNo)
                .orderByDesc(RefundOrder::getId)
                .last("LIMIT 1"));
    }

    /** out_refund_no = out_trade_no + "-R"，25 + 2 = 27 位，合法字符集 */
    private static String buildOutRefundNo(String outTradeNo) {
        return outTradeNo + REFUND_NO_SUFFIX;
    }

    private static RefundCommand toRefundCommand(PaymentOrder order, RefundOrder refund) {
        RefundCommand cmd = new RefundCommand();
        cmd.setOutTradeNo(order.getOutTradeNo());
        cmd.setOutRefundNo(refund.getOutRefundNo());
        cmd.setAmountFen(refund.getAmountFen());
        cmd.setReason(refund.getReason());
        return cmd;
    }

    private static String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > REASON_MAX_LENGTH ? reason.substring(0, REASON_MAX_LENGTH) : reason;
    }

    /** 退款结果通知：通知失败绝不影响退款状态（钱已经动了，通知只是体验） */
    private void notifyRefund(PaymentOrder order, boolean success) {
        String amount = MoneyUtils.formatYuan(order.getAmountFen());
        String bizDesc = describeBiz(order);
        try {
            if (success) {
                notificationService.notifyUser(order.getUserId(), "refund_success", "退款到账",
                        "您的" + bizDesc + "款项 " + amount + " 已原路退回，1-3 个工作日到账。",
                        order.getBizType(), order.getBizId());
            } else {
                notificationService.notifyUser(order.getUserId(), "refund_failed", "退款失败",
                        "您的" + bizDesc + "款项 " + amount + " 退款未成功，请联系客服处理。",
                        order.getBizType(), order.getBizId());
            }
        } catch (Exception e) {
            log.warn("[Refund] 退款通知发送失败（不影响退款状态） outTradeNo={}", order.getOutTradeNo(), e);
        }
    }

    private static String describeBiz(PaymentOrder order) {
        return switch (order.getBizType()) {
            case PaymentOrder.BIZ_APPOINTMENT -> "预约挂号";
            case PaymentOrder.BIZ_PRESCRIPTION -> "处方药费";
            case PaymentOrder.BIZ_RECHARGE -> "钱包充值";
            default -> "订单";
        };
    }

    /** 退款回调的“本端拒收”路径：落 ERROR 并要求网关重试 */
    private String rejectRefund(PaymentNotifyLog logRow, String errorMsg) {
        logRow.setProcessResult(PaymentNotifyLog.RESULT_ERROR);
        logRow.setErrorMsg(errorMsg);
        paymentNotifyLogMapper.insert(logRow);
        log.error("[Refund] 退款回调本端拒收：{}", errorMsg);
        return PaymentNotifyLog.RESULT_ERROR;
    }
}
