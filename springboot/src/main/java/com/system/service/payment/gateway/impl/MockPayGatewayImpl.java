package com.system.service.payment.gateway.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.common.BusinessException;
import com.system.config.WeChatPayProperties;
import com.system.entity.PaymentOrder;
import com.system.entity.RefundOrder;
import com.system.mapper.PaymentOrderMapper;
import com.system.mapper.RefundOrderMapper;
import com.system.service.payment.gateway.PaymentGateway;
import com.system.service.payment.model.PayNotifyPayload;
import com.system.service.payment.model.PayQueryResult;
import com.system.service.payment.model.PrepayCommand;
import com.system.service.payment.model.PrepayResult;
import com.system.service.payment.model.RefundCommand;
import com.system.service.payment.model.RefundResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock 支付网关：在不具备商户资质的前提下，复刻真实微信支付的**硬约束**，
 * 让上层业务骨架（PaymentService / PaymentBizHandler）无需改动即可在 S5 换成真通道。
 *
 * <p>刻意复刻的四条真实约束：
 * <ol>
 *   <li>out_trade_no 只允许 数字/字母/_/-/|/*，长度 6~32，全局唯一；</li>
 *   <li>金额单位是“分”，且必须大于 0；</li>
 *   <li>已 PAID / CLOSED 的支付单不能再次支付；关单必须幂等；</li>
 *   <li>回调验签失败时**不抛异常**，而是降级为 sign_verified=0 交由上层记账。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wechat.pay.channel", havingValue = "mock", matchIfMissing = true)
public class MockPayGatewayImpl implements PaymentGateway {

    /** 与 PaymentOrder.channel 的取值保持一致 */
    public static final String CHANNEL = "MOCK";

    /** Mock 回调签名请求头（真实微信是 Wechatpay-Signature 等四个头） */
    public static final String NOTIFY_SIGN_HEADER = "X-Mock-Signature";

    private static final String ALNUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final WeChatPayProperties payProperties;
    private final PaymentOrderMapper paymentOrderMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final ObjectMapper objectMapper;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public PrepayResult prepay(PrepayCommand cmd) {
        // 约束①：单号字符集与长度
        if (cmd.getOutTradeNo() == null || !cmd.getOutTradeNo().matches("[0-9A-Za-z_\\-|*]{6,32}")) {
            throw new BusinessException("out_trade_no 不合法：只允许数字/字母/_/-/|/*，长度 6~32");
        }
        // 约束②：金额必须大于 0 分
        if (cmd.getAmountFen() <= 0) {
            throw new BusinessException("支付金额必须大于 0 分");
        }
        // 约束③：已付款/已关闭的单不允许再次发起支付
        PaymentOrder existing = selectByOutTradeNo(cmd.getOutTradeNo());
        if (existing != null && !PaymentOrder.STATUS_CREATED.equals(existing.getStatus())) {
            throw new BusinessException("支付单当前状态不可支付：" + existing.getStatus());
        }
        // 约束④：有 expire_time 的单必须能被关单；已过期的不允许再发起支付
        if (existing != null && existing.getExpireTime() != null
                && existing.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("支付单已过期，请重新下单：" + cmd.getOutTradeNo());
        }

        PrepayResult result = new PrepayResult();
        result.setPrepayId("MOCK_PREPAY_" + randomToken(16));
        result.setOutTradeNo(cmd.getOutTradeNo());

        // Mock 阶段不产出 wx.requestPayment 六参数，直接给收银台地址，便于浏览器验收
        Map<String, String> payParams = new LinkedHashMap<>();
        payParams.put("outTradeNo", cmd.getOutTradeNo());
        payParams.put("cashierUrl", payProperties.getMock().getBaseUrl() + "/mock/pay/" + cmd.getOutTradeNo());
        result.setPayParams(payParams);

        log.info("[MockPay] 下单成功 outTradeNo={} amountFen={} prepayId={}",
                cmd.getOutTradeNo(), cmd.getAmountFen(), result.getPrepayId());
        return result;
    }

    @Override
    public PayQueryResult query(String outTradeNo) {
        PaymentOrder order = selectByOutTradeNo(outTradeNo);
        PayQueryResult result = new PayQueryResult();
        result.setOutTradeNo(outTradeNo);
        if (order == null) {
            result.setPaid(false);
            result.setAmountFen(0L);
            result.setTradeState("NOTFOUND");
            return result;
        }
        boolean paid = PaymentOrder.STATUS_PAID.equals(order.getStatus());
        result.setPaid(paid);
        result.setTransactionId(order.getTransactionId());
        result.setAmountFen(order.getAmountFen());
        result.setTradeState(paid ? "SUCCESS" : "NOTPAY");
        return result;
    }

    @Override
    public void close(String outTradeNo) {
        PaymentOrder order = selectByOutTradeNo(outTradeNo);
        if (order == null) {
            // 约束③：关单幂等 —— 单子不存在也当关成功，不抛异常打断上层清理逻辑
            log.warn("[MockPay] 关单时支付单不存在（视作已关）：{}", outTradeNo);
            return;
        }
        if (PaymentOrder.STATUS_PAID.equals(order.getStatus())) {
            throw new BusinessException("已支付的支付单不能关单：" + outTradeNo);
        }
        log.info("[MockPay] 关单成功 outTradeNo={} 当前状态={}", outTradeNo, order.getStatus());
    }

    @Override
    public RefundResult refund(RefundCommand cmd) {
        // 复刻真实约束：原支付单必须存在且已支付/退款中，退款金额不能超过原单
        PaymentOrder order = selectByOutTradeNo(cmd.getOutTradeNo());
        if (order == null) {
            throw new BusinessException("退款失败：支付单不存在 " + cmd.getOutTradeNo());
        }
        if (!PaymentOrder.STATUS_PAID.equals(order.getStatus())
                && !PaymentOrder.STATUS_REFUNDING.equals(order.getStatus())) {
            throw new BusinessException("退款失败：支付单当前状态不可退款：" + order.getStatus());
        }
        if (cmd.getAmountFen() <= 0 || cmd.getAmountFen() > order.getAmountFen()) {
            throw new BusinessException("退款金额不合法：" + cmd.getAmountFen()
                    + "（原单仅 " + order.getAmountFen() + " 分）");
        }

        // ★真实微信退款是**异步**的：这里只代表“网关已受理”，
        //   返回 PROCESSING，最终结果必须由 refund_notify 回推。
        //   这样 S4 的退款状态机、对账重试、幂等才会被真正走到（同步返回 SUCCESS 等于把 S4 短路掉）。
        RefundResult result = new RefundResult();
        result.setSuccess(true);
        result.setRefundId("MOCKREFUND_" + randomToken(16));
        result.setState(RefundOrder.STATUS_PROCESSING);
        log.info("[MockPay] 退款已受理（异步）outTradeNo={} outRefundNo={} amountFen={} reason={} refundId={}",
                cmd.getOutTradeNo(), cmd.getOutRefundNo(), cmd.getAmountFen(), cmd.getReason(), result.getRefundId());
        return result;
    }

    @Override
    public RefundResult queryRefund(String outRefundNo) {
        // Mock 通道的“网关侧状态”就落在本项目的 refund_order 表里
        RefundOrder refund = refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrder>()
                .eq(RefundOrder::getOutRefundNo, outRefundNo)
                .orderByDesc(RefundOrder::getId)
                .last("LIMIT 1"));
        RefundResult result = new RefundResult();
        if (refund == null) {
            result.setSuccess(false);
            result.setState("NOTFOUND");
            return result;
        }
        result.setRefundId(refund.getRefundId());
        result.setState(refund.getStatus());
        // 只有 SUCCESS 才算“退成功”；PROCESSING 仍然是未决，不能让上层误判
        result.setSuccess(RefundOrder.STATUS_SUCCESS.equals(refund.getStatus()));
        return result;
    }

    @Override
    public PayNotifyPayload parseNotify(HttpServletRequest req) {
        String rawBody = readBody(req);
        PayNotifyPayload payload = new PayNotifyPayload();
        payload.setRawBody(rawBody);
        payload.setNotifyType("PAY");

        // 约束④：验签失败不抛异常，交给上层落 ERROR 日志并返回 200
        String provided = req.getHeader(NOTIFY_SIGN_HEADER);
        String expected = hmacSha256Hex(rawBody);
        if (provided == null || !constantTimeEquals(expected, provided)) {
            payload.setSignVerified(false);
            payload.setErrorMsg("Mock 回调验签失败");
            log.warn("[MockPay] 回调验签失败，bodyLength={}", rawBody.length());
            return payload;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);
            payload.setOutTradeNo(asString(body.get("out_trade_no")));
            payload.setOutRefundNo(asString(body.get("out_refund_no")));
            payload.setTransactionId(asString(body.get("transaction_id")));
            payload.setAmountFen(asLong(body.get("amount_fen")));
            payload.setSuccess(Boolean.TRUE.equals(body.get("success")));
            String notifyType = asString(body.get("notify_type"));
            if (notifyType != null && !notifyType.isEmpty()) {
                payload.setNotifyType(notifyType);
            }
            payload.setSignVerified(true);
        } catch (IOException e) {
            payload.setSignVerified(false);
            payload.setErrorMsg("Mock 回调报文解析失败：" + e.getMessage());
        }
        return payload;
    }

    // ==================== 供 MockPayController 复用的工具方法 ====================

    /** HMAC-SHA256(共享密钥, 原始 body) → 小写 hex。S5 换成微信平台证书 RSA 验签时，只改 parseNotify。 */
    public String hmacSha256Hex(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    payProperties.getMock().getSignKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("Mock 回调签名计算失败：" + e.getMessage());
        }
    }

    /** 生成指定长度的随机字母数字串（用于 prepay_id / transaction_id / nonce） */
    public String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALNUM.charAt(random.nextInt(ALNUM.length())));
        }
        return sb.toString();
    }

    // ==================== 内部工具 ====================

    private PaymentOrder selectByOutTradeNo(String outTradeNo) {
        return paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOutTradeNo, outTradeNo));
    }

    /** 逐字节读取原始请求体（验签必须用原始字节，不能走 @RequestBody） */
    private String readBody(HttpServletRequest req) {
        try (InputStream in = req.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取回调原始报文失败：" + e.getMessage());
        }
    }

    /** 定长比较，防时序侧信道（真实验签同款习惯） */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
