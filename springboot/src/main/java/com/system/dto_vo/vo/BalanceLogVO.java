package com.system.dto_vo.vo;

import com.system.entity.BalanceLog;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BalanceLogVO {

    private Long id;
    private String type;
    private String typeText;
    private Long amountFen;
    private Long balanceAfterFen;
    private Long orderId;
    private String payMethod;
    private String payMethodText;
    private String remark;
    private LocalDateTime createTime;

    public static BalanceLogVO fromEntity(BalanceLog log) {
        BalanceLogVO vo = new BalanceLogVO();
        vo.setId(log.getId());
        vo.setType(log.getType());
        vo.setTypeText(typeLabel(log.getType()));
        vo.setAmountFen(log.getAmountFen());
        vo.setBalanceAfterFen(log.getBalanceAfterFen());
        vo.setOrderId(log.getOrderId());
        vo.setPayMethod(log.getPayMethod());
        vo.setPayMethodText(payMethodLabel(log.getPayMethod()));
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    public static String typeLabel(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "recharge" -> "余额充值";
            case "order_pay" -> "预约支付";
            case "order_refund" -> "预约退款";
            case "prescription_pay" -> "处方支付";
            default -> type;
        };
    }

    public static String payMethodLabel(String payMethod) {
        if (payMethod == null || payMethod.isBlank()) {
            return "";
        }
        return switch (payMethod) {
            case "balance" -> "余额支付";
            case "wechat" -> "微信支付";
            case "alipay" -> "支付宝";
            default -> payMethod;
        };
    }

}
