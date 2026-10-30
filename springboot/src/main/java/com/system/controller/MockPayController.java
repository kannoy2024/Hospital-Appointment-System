package com.system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.common.BusinessException;
import com.system.config.WeChatPayProperties;
import com.system.dto_vo.vo.RefundOrderVO;
import com.system.entity.PaymentOrder;
import com.system.service.payment.PaymentService;
import com.system.service.payment.gateway.impl.MockPayGatewayImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock 收银台：只在 wechat.pay.channel=mock 时存在。
 *
 * <p>它模拟的是“用户在微信里完成支付”这一步，并按**真实微信回调的格式与验签方式**
 * 把结果打回本服务的 {@code /api/pay/notify}——也就是说，验收走的完全是生产同一条链路，
 * S5 换真通道时不需要动任何业务代码。
 *
 * <p>三个按钮分别对应真实世界的三种结果：支付成功 / 用户取消 / 支付超时。
 * 其中「支付超时」会走 payment 模块自己的关单逻辑，用来验证“先关单、再改业务”。
 */
@Slf4j
@RestController
@RequestMapping("/mock/pay")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wechat.pay.channel", havingValue = "mock", matchIfMissing = true)
public class MockPayController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String NOTIFY_PATH = "/api/pay/notify";
    private static final String REFUND_NOTIFY_PATH = "/api/pay/refund-notify";

    private final PaymentService paymentService;
    private final MockPayGatewayImpl mockPayGateway;
    private final WeChatPayProperties payProperties;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.create();

    /** 收银台页面：/mock/pay/{outTradeNo} */
    @GetMapping("/{outTradeNo}")
    public void cashier(@PathVariable String outTradeNo, HttpServletResponse response) throws IOException {
        PaymentOrder order = paymentService.getEntityByOutTradeNo(outTradeNo);
        writeHtml(response, renderPage(outTradeNo, order));
    }

    /**
     * 点「退款成功」：模拟真实微信退款结果通知（POST /api/pay/refund-notify）。
     *
     * <p>只有支付单处于 REFUNDING 才可点——这正是 Mock 复刻真实约束的地方：
     * 退款是异步的，必须由网关回调来宣布结果，业务方不能自己判定退款成功。
     */
    @PostMapping("/{outTradeNo}/refund-success")
    public Map<String, Object> refundSuccess(@PathVariable String outTradeNo) {
        return sendRefundNotify(outTradeNo, true);
    }

    /** 点「退款失败」：验证 REFUNDING → PAID 回滚且允许重试 */
    @PostMapping("/{outTradeNo}/refund-fail")
    public Map<String, Object> refundFail(@PathVariable String outTradeNo) {
        return sendRefundNotify(outTradeNo, false);
    }

    /** 点「支付成功」：按真实格式组装回调，HMAC 签名后 POST 回 /api/pay/notify */
    @PostMapping("/{outTradeNo}/success")
    public Map<String, Object> success(@PathVariable String outTradeNo) {
        PaymentOrder order = requireCreated(outTradeNo);
        Map<String, Object> notifyBody = new LinkedHashMap<>();
        notifyBody.put("notify_type", "PAY");
        notifyBody.put("out_trade_no", outTradeNo);
        notifyBody.put("transaction_id", "MOCKTXN" + mockPayGateway.randomToken(20));
        notifyBody.put("amount_fen", order.getAmountFen());
        notifyBody.put("payer_openid", order.getPayerOpenid());
        notifyBody.put("success", true);

        Map<String, Object> result = postNotify(notifyBody, payProperties.notifyBaseUrl());
        return Map.of("code", 200, "message", "已向 /api/pay/notify 发送支付成功回调", "notifyResponse", result);
    }


    /** 点「用户取消」：不动业务，只把支付单关掉（业务单由各自的超时任务负责回滚） */
    @PostMapping("/{outTradeNo}/cancel")
    public Map<String, Object> cancel(@PathVariable String outTradeNo) {
        boolean closed = paymentService.closeIfCreated(outTradeNo);
        return Map.of("code", 200, "message", closed ? "支付单已关闭" : "支付单当前状态无需关闭");
    }

    /** 点「支付超时」：完全等同真实世界超过 time_expire，走关单逻辑 */
    @PostMapping("/{outTradeNo}/expire")
    public Map<String, Object> expire(@PathVariable String outTradeNo) {
        boolean closed = paymentService.closeIfCreated(outTradeNo);
        return Map.of("code", 200, "message", closed ? "已按超时关单" : "支付单当前状态无需关闭");
    }

    // ==================== 内部 ====================

    /** 组装并投递一条退款结果通知 */
    private Map<String, Object> sendRefundNotify(String outTradeNo, boolean success) {
        PaymentOrder order = paymentService.getEntityByOutTradeNo(outTradeNo);
        if (order == null) {
            throw new BusinessException("支付单不存在：" + outTradeNo);
        }
        if (!PaymentOrder.STATUS_REFUNDING.equals(order.getStatus())) {
            throw new BusinessException("支付单当前状态没有待处理的退款：" + order.getStatus());
        }
        RefundOrderVO refund = paymentService.getRefundByOutTradeNo(outTradeNo);
        if (refund == null) {
            throw new BusinessException("支付单处于 REFUNDING 却没有退款单，请先调用取消接口发起退款");
        }

        Map<String, Object> notifyBody = new LinkedHashMap<>();
        notifyBody.put("notify_type", "REFUND");
        notifyBody.put("out_trade_no", outTradeNo);
        notifyBody.put("out_refund_no", refund.getOutRefundNo());
        notifyBody.put("transaction_id", order.getTransactionId());
        notifyBody.put("amount_fen", refund.getAmountFen());
        notifyBody.put("success", success);

        Map<String, Object> result = postNotify(notifyBody, payProperties.refundNotifyBaseUrl());
        return Map.of("code", 200,
                "message", "已向 " + REFUND_NOTIFY_PATH + " 发送退款" + (success ? "成功" : "失败") + "回调",
                "notifyResponse", result);
    }

    private Map<String, Object> postNotify(Map<String, Object> notifyBody, String notifyUrl) {
        String json;
        try {
            json = objectMapper.writeValueAsString(notifyBody);
        } catch (Exception e) {
            throw new BusinessException("组装 Mock 回调报文失败：" + e.getMessage());
        }
        // ★签名必须对“即将发送的那串字节”计算，与真实微信 Wechatpay-Signature 同理
        String signature = mockPayGateway.hmacSha256Hex(json);

        Map<String, Object> response;
        try {
            String raw = restClient.post()
                    .uri(notifyUrl)
                    .header(MockPayGatewayImpl.NOTIFY_SIGN_HEADER, signature)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(json)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> parsed = objectMapper.readValue(
                    raw == null ? "{}" : raw, new TypeReference<Map<String, Object>>() {
                    });
            response = parsed;
            log.info("[MockPay] 回调已投递 url={} 应答={}", notifyUrl, response);
        } catch (Exception e) {
            log.error("[MockPay] 回调投递失败 url={}", notifyUrl, e);
            throw new BusinessException("Mock 回调投递失败：" + e.getMessage());
        }
        return response;
    }

    private PaymentOrder requireCreated(String outTradeNo) {
        PaymentOrder order = paymentService.getEntityByOutTradeNo(outTradeNo);
        if (order == null) {
            throw new BusinessException("支付单不存在：" + outTradeNo);
        }
        if (!PaymentOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new BusinessException("支付单当前状态不可支付：" + order.getStatus());
        }
        return order;
    }

    private String renderPage(String outTradeNo, PaymentOrder order) {
        if (order == null) {
            return page("支付单不存在", "<p class=\"warn\">没有找到单号 <code>" + escape(outTradeNo)
                    + "</code>，请确认已通过下单接口创建支付单。</p>", null);
        }

        String statusText = order.getStatus();
        StringBuilder info = new StringBuilder();
        info.append("<ul class=\"info\">")
                .append("<li>商品：").append(escape(order.getDescription() == null ? "医疗服务" : order.getDescription())).append("</li>")
                .append("<li>金额：<b>").append(yuan(order.getAmountFen())).append("</b> <span class=\"dim\">（")
                .append(order.getAmountFen()).append(" 分）</span></li>")
                .append("<li>商户单号：<code>").append(escape(outTradeNo)).append("</code></li>")
                .append("<li>业务：").append(escape(order.getBizType())).append(" #").append(order.getBizId()).append("</li>")
                .append("<li>通道：").append(escape(order.getChannel())).append("　支付方式：")
                .append(escape(order.getPayMethod())).append("</li>")
                .append("<li>过期时间：")
                .append(order.getExpireTime() == null ? "-" : order.getExpireTime().format(TIME_FMT))
                .append("</li>")
                .append("<li>当前状态：<span class=\"st st-").append(escape(statusText)).append("\">")
                .append(escape(statusText)).append("</span></li>")
                .append("</ul>");

        if (PaymentOrder.STATUS_REFUNDING.equals(statusText)) {
            return page("退款处理中", info.toString()
                    + "<p class=\"warn\">这笔单正在退款中。真实微信退款是<b>异步</b>的，必须由网关回调才能确定结果——"
                    + "下面两个按钮模拟的就是那条回调（<code>" + escape(REFUND_NOTIFY_PATH) + "</code>）。</p>",
                    refundActions(outTradeNo));
        }
        if (!PaymentOrder.STATUS_CREATED.equals(statusText)) {
            return page("支付单已处理", info.toString()
                    + "<p class=\"warn\">该支付单当前状态为 <b>" + escape(statusText)
                    + "</b>，不能再支付（复刻真实网关“已支付/已关闭的单不能再次支付”的约束）。</p>", null);
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            return page("支付单已过期", info.toString()
                    + "<p class=\"warn\">已超过过期时间，请点「模拟支付超时」走关单流程，或退回上一页重新下单。</p>",
                    actions(outTradeNo));
        }
        return page("Mock 收银台", info.toString()
                + "<p class=\"dim\">选择一种结果，系统会按真实微信回调的报文格式与签名方式打回 <code>"
                + escape(NOTIFY_PATH) + "</code>。</p>", actions(outTradeNo));
    }

    private String actions(String outTradeNo) {
        return "<div class=\"actions\">"
                + "<button class=\"pay\" onclick=\"act('success')\">支付成功</button>"
                + "<button class=\"cancel\" onclick=\"act('cancel')\">用户取消</button>"
                + "<button class=\"expire\" onclick=\"act('expire')\">模拟支付超时</button>"
                + "</div>"
                + "<pre id=\"out\" class=\"out\">等待操作…</pre>"
                + "<script>"
                + "function act(k){"
                + "var o=document.getElementById('out');o.textContent='请求中…';"
                + "fetch('/mock/pay/" + outTradeNo + "/'+k,{method:'POST'})"
                + ".then(function(r){return r.json();})"
                + ".then(function(j){o.textContent=JSON.stringify(j,null,2);"
                + "setTimeout(function(){location.reload();},1200);})"
                + ".catch(function(e){o.textContent='请求失败：'+e;});}"
                + "</script>";
    }

    private String refundActions(String outTradeNo) {
        return "<div class=\"actions\">"
                + "<button class=\"pay\" onclick=\"act('refund-success')\">退款成功</button>"
                + "<button class=\"cancel\" onclick=\"act('refund-fail')\">退款失败</button>"
                + "</div>"
                + "<pre id=\"out\" class=\"out\">等待操作…</pre>"
                + "<script>"
                + "function act(k){"
                + "var o=document.getElementById('out');o.textContent='请求中…';"
                + "fetch('/mock/pay/" + outTradeNo + "/'+k,{method:'POST'})"
                + ".then(function(r){return r.json();})"
                + ".then(function(j){o.textContent=JSON.stringify(j,null,2);"
                + "setTimeout(function(){location.reload();},1200);})"
                + ".catch(function(e){o.textContent='请求失败：'+e;});}"
                + "</script>";
    }

    private String page(String title, String body, String script) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(title) + "</title><style>"
                + "*{box-sizing:border-box}body{margin:0;padding:24px;background:#f4f5f7;"
                + "font-family:-apple-system,'Segoe UI','Microsoft YaHei',sans-serif;color:#1f2329}"
                + ".card{max-width:520px;margin:0 auto;background:#fff;border-radius:12px;padding:24px;"
                + "box-shadow:0 2px 12px rgba(0,0,0,.08)}"
                + "h1{font-size:20px;margin:0 0 4px}.tag{display:inline-block;font-size:12px;color:#8a8f99;"
                + "border:1px solid #e3e5e8;border-radius:10px;padding:2px 8px;margin-bottom:16px}"
                + "ul.info{list-style:none;padding:0;margin:0 0 12px}ul.info li{padding:6px 0;font-size:14px;"
                + "border-bottom:1px dashed #eef0f3;display:flex;justify-content:space-between;gap:12px}"
                + ".dim{color:#8a8f99;font-size:12px}"
                + ".st{font-weight:600}.st-CREATED{color:#d48806}.st-PAID{color:#237804}"
                + ".st-CLOSED{color:#8c8c8c}.st-FAILED{color:#cf1322}"
                + ".st-REFUNDING{color:#d48806}.st-REFUNDED{color:#237804}"
                + ".actions{display:grid;gap:10px;margin:18px 0 12px}"
                + "button{padding:12px;border:0;border-radius:8px;font-size:15px;cursor:pointer;color:#fff}"
                + ".pay{background:#07c160}.cancel{background:#8a8f99}.expire{background:#d48806}"
                + "button:hover{filter:brightness(.94)}"
                + ".out{background:#1f2329;color:#a6e3a1;padding:12px;border-radius:8px;font-size:12px;"
                + "overflow:auto;max-height:220px;white-space:pre-wrap}"
                + ".warn{background:#fff7e6;border:1px solid #ffe7ba;color:#ad6800;padding:10px 12px;"
                + "border-radius:8px;font-size:13px}code{background:#f2f3f5;padding:1px 4px;border-radius:4px}"
                + "</style></head><body><div class=\"card\"><h1>" + escape(title) + "</h1>"
                + "<div class=\"tag\">DSH Mock Pay · 仅本地演示</div>" + body + "</div>" + script
                + "</body></html>";
    }

    private void writeHtml(HttpServletResponse response, String html) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(html);
    }

    /** 分 → 「¥12.00」 */
    private String yuan(long fen) {
        return "¥" + BigDecimal.valueOf(fen, 2).toPlainString();
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
