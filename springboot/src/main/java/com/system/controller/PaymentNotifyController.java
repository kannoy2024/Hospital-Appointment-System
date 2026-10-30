package com.system.controller;

import com.system.entity.PaymentNotifyLog;
import com.system.service.payment.PaymentService;
import com.system.service.payment.gateway.PaymentGateway;
import com.system.service.payment.model.PayNotifyPayload;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关异步回调入口（必须 permitAll，回调来自网关、没有 JWT）。
 *
 * <p>★关键：这里**绝对不能**用 {@code @RequestBody} 接参数。
 * 真实微信验签用的是请求体原始字节，一旦让 Jackson 反序列化再 toString 回去，
 * 空格/字段顺序/转义都会变，签名必然不匹配。所以只能从 {@link HttpServletRequest}
 * 读 InputStream 原始字节，解析交给 {@link PaymentGateway#parseNotify}。
 *
 * <p>应答格式按微信规范：成功 {@code {"code":"SUCCESS","message":"成功"}}；
 * 非 200 会触发网关重试（微信为 15s/15s/30s/3m/10m/20m/30m/30m 共 8 次）。
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PaymentNotifyController {

    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;

    /** 支付结果通知 */
    @PostMapping("/notify")
    public Map<String, String> payNotify(HttpServletRequest request) {
        return dispatch(request, "PAY");
    }

    /** 退款结果通知：走退款单状态机，绝不与支付通知混用同一个处理器 */
    @PostMapping("/refund-notify")
    public Map<String, String> refundNotify(HttpServletRequest request) {
        return dispatch(request, PaymentNotifyLog.TYPE_REFUND);
    }

    private Map<String, String> dispatch(HttpServletRequest request, String notifyType) {
        PayNotifyPayload payload;
        try {
            payload = paymentGateway.parseNotify(request);
        } catch (Exception e) {
            // 解析失败也要回 200：反复重试同样的坏报文没有意义，问题已经在本端日志里
            log.error("[PayNotify] {} 报文解析失败", notifyType, e);
            return fail("报文解析失败");
        }
        payload.setNotifyType(notifyType);

        // 回调一律返回 200，用 body 里的 code 表达处理结果，避免网关因为我们的业务异常无限重试
        try {
            boolean refund = PaymentNotifyLog.TYPE_REFUND.equals(notifyType);
            String result = refund
                    ? paymentService.handleRefundNotify(payload)
                    : paymentService.handlePayNotify(payload);
            if (PaymentNotifyLog.RESULT_ERROR.equals(result)) {
                // 验签失败/金额不符属于“本端拒收”，必须让网关看到失败并重试，不能吞掉。
                // 注意 REFUND_FAILED 不属于这一类：网关已经明确告诉我们退款失败了，
                // 本端也已正确记账，再让它重试 8 次毫无意义。
                return fail("处理失败");
            }
            return ok();
        } catch (Exception e) {
            log.error("[PayNotify] {} 处理异常 outTradeNo={}", notifyType, payload.getOutTradeNo(), e);
            return fail("处理异常");
        }
    }

    private Map<String, String> ok() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", "SUCCESS");
        body.put("message", "成功");
        return body;
    }

    private Map<String, String> fail(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", "FAIL");
        body.put("message", message);
        return body;
    }
}
