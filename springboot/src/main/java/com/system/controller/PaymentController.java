package com.system.controller;

import com.system.common.Result;
import com.system.dto_vo.vo.PaymentOrderVO;
import com.system.dto_vo.vo.RefundOrderVO;
import com.system.entity.PaymentOrder;
import com.system.security.SecurityUtils;
import com.system.entity.User;
import com.system.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付单查询：前端在收银台支付完成后轮询它拿最终状态。
 */
@Tag(name = "支付单")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "按商户单号查询支付单状态（仅限本人）")
    @GetMapping("/{outTradeNo}")
    public Result<PaymentOrderVO> getByOutTradeNo(@PathVariable String outTradeNo) {
        PaymentOrder order = paymentService.getEntityByOutTradeNo(outTradeNo);
        if (order == null) {
            return Result.fail("支付单不存在");
        }
        User currentUser = SecurityUtils.getCurrentUserEntity();
        Long currentUserId = currentUser == null ? null : currentUser.getId();
        if (currentUserId != null && order.getUserId() != null && !currentUserId.equals(order.getUserId())) {
            return Result.fail("无权查看他人支付单");
        }
        return Result.ok(PaymentOrderVO.fromEntity(order));
    }

    @Operation(summary = "按商户单号查询退款单状态（仅限本人）")
    @GetMapping("/{outTradeNo}/refund")
    public Result<RefundOrderVO> getRefundByOutTradeNo(@PathVariable String outTradeNo) {
        PaymentOrder order = paymentService.getEntityByOutTradeNo(outTradeNo);
        if (order == null) {
            return Result.fail("支付单不存在");
        }
        User currentUser = SecurityUtils.getCurrentUserEntity();
        Long currentUserId = currentUser == null ? null : currentUser.getId();
        if (currentUserId != null && order.getUserId() != null && !currentUserId.equals(order.getUserId())) {
            return Result.fail("无权查看他人退款单");
        }
        RefundOrderVO refund = paymentService.getRefundByOutTradeNo(outTradeNo);
        if (refund == null) {
            // 正常情况：这笔单压根没退过款。前端据此把「退款中」的占位去掉即可
            return Result.fail("该支付单没有退款记录");
        }
        return Result.ok(refund);
    }
}
