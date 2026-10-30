package com.system.dto_vo.vo;

import com.system.entity.PaymentOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 支付单视图：给前端轮询。金额字段带 Fen 后缀，与金额重构后的约定一致（值单位为分）。 */
@Data
public class PaymentOrderVO {

    @Schema(description = "商户支付单号")
    private String outTradeNo;

    @Schema(description = "业务类型：APPOINTMENT/PRESCRIPTION/RECHARGE")
    private String bizType;

    @Schema(description = "业务单 id")
    private Long bizId;

    /** 越权校验用；返回给前端无害，也便于联调 */
    @Schema(description = "付款用户 id")
    private Long userId;

    @Schema(description = "支付金额（单位：分）")
    private Long amountFen;

    @Schema(description = "CREATED/PAID/REFUNDING/REFUNDED/CLOSED/FAILED")
    private String status;

    @Schema(description = "支付方式：wechat/alipay/balance")
    private String payMethod;

    @Schema(description = "支付通道：MOCK/WECHAT")
    private String channel;

    @Schema(description = "网关预支付 id")
    private String prepayId;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "支付单过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "支付完成时间")
    private LocalDateTime paidTime;

    /**
     * 显式逐字段 set，刻意不用 BeanUtils.copyProperties：
     * 实体 amountFen 是基本类型 long、VO 是 Long，跨类型拷贝一旦改名就静默变 null（M 阶段已踩过）。
     */
    public static PaymentOrderVO fromEntity(PaymentOrder order) {
        if (order == null) {
            return null;
        }
        PaymentOrderVO vo = new PaymentOrderVO();
        vo.setOutTradeNo(order.getOutTradeNo());
        vo.setBizType(order.getBizType());
        vo.setBizId(order.getBizId());
        vo.setUserId(order.getUserId());
        vo.setAmountFen(order.getAmountFen());
        vo.setStatus(order.getStatus());
        vo.setPayMethod(order.getPayMethod());
        vo.setChannel(order.getChannel());
        vo.setPrepayId(order.getPrepayId());
        vo.setDescription(order.getDescription());
        vo.setExpireTime(order.getExpireTime());
        vo.setPaidTime(order.getPaidTime());
        return vo;
    }
}
