package com.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付通道配置，绑定 application.yml 的 wechat.pay 段。
 * S1 只用 channel 与 mock.*；微信那几项留到 S5。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.pay")
public class WeChatPayProperties {

    /** 支付通道：mock | wechat（S5 才切 wechat） */
    private String channel = "mock";

    /** 微信支付回调地址（S5 用） */
    private String notifyUrl;

    /** 微信退款回调地址（S5 用） */
    private String refundNotifyUrl;

    /** Mock 收银台地址，用于把回调打回自己 */
    private Mock mock = new Mock();

    /**
     * 回调落点：优先用显式配置的 notify-url；
     * 未配置时（本地 Mock 场景）退回 base-url + /api/pay/notify。
     * 回调地址必须由本端拼接，不允许调用方（业务层）随便传，否则 Mock 会把回调打到错误地址。
     */
    public String notifyBaseUrl() {
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            return notifyUrl;
        }
        return mock.getBaseUrl() + "/api/pay/notify";
    }

    /**
     * 退款回调落点：与 {@link #notifyBaseUrl()} 同规则，退回 base-url + /api/pay/refund-notify。
     *
     * <p>真实微信里这两个地址必须分开配置（退款结果走 refund_notify_url），
     * 混用一个入口会导致退款通知被当成支付通知处理。
     */
    public String refundNotifyBaseUrl() {
        if (refundNotifyUrl != null && !refundNotifyUrl.isBlank()) {
            return refundNotifyUrl;
        }
        return mock.getBaseUrl() + "/api/pay/refund-notify";
    }

    @Data
    public static class Mock {
        /** 本地 Mock 收银台地址，如 http://127.0.0.1:9090 */
        private String baseUrl = "http://127.0.0.1:9090";
        /** Mock 支付单有效期（分钟），与预约超时同源 */
        private int prepayExpireMinutes = 30;
        /**
         * Mock 回调验签共享密钥。
         * 注意：yml 的 wechat.pay.mock 下目前没有 sign-key 这一项，
         * 所以这里给一个仅用于本地演示的默认值；补 yml 后会自然覆盖。
         */
        private String signKey = "dsh-mock-pay-sign-key-please-change";
    }
}
