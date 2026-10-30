package com.system.dto_vo.vo;

import com.system.entity.RefundOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 退款单视图：前端在取消已支付预约后轮询它拿退款最终状态。金额字段带 Fen 后缀，值单位为分。 */
@Data
public class RefundOrderVO {

    @Schema(description = "商户退款单号（= 支付单号 + \"-R\"）")
    private String outRefundNo;

    @Schema(description = "原商户支付单号")
    private String outTradeNo;

    @Schema(description = "PROCESSING/SUCCESS/FAILED/CLOSED")
    private String status;

    @Schema(description = "退款金额（单位：分）")
    private Long amountFen;

    @Schema(description = "退款原因")
    private String reason;

    @Schema(description = "网关侧退款单号")
    private String refundId;

    @Schema(description = "退款成功时间")
    private LocalDateTime successTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 显式逐字段 set，刻意不用 BeanUtils.copyProperties：
     * 实体 amountFen 是基本类型 long、VO 是 Long，改名即静默变 null（M 阶段已踩过）。
     */
    public static RefundOrderVO fromEntity(RefundOrder refund) {
        if (refund == null) {
            return null;
        }
        RefundOrderVO vo = new RefundOrderVO();
        vo.setOutRefundNo(refund.getOutRefundNo());
        vo.setOutTradeNo(refund.getOutTradeNo());
        vo.setStatus(refund.getStatus());
        vo.setAmountFen(refund.getAmountFen());
        vo.setReason(refund.getReason());
        vo.setRefundId(refund.getRefundId());
        vo.setSuccessTime(refund.getSuccessTime());
        vo.setCreateTime(refund.getCreateTime());
        return vo;
    }
}
