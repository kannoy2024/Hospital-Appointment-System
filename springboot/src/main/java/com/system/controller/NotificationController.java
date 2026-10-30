package com.system.controller;

import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.vo.NotificationVO;
import com.system.security.SecurityUtils;
import com.system.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "消息通知", description = "站内消息")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/notifications")
@AllArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的消息分页")
    @GetMapping
    public Result<PageResult<NotificationVO>> pageMine(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Boolean unreadOnly) {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();
        return Result.ok(notificationService.pageMine(userId, current, size, unreadOnly));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();
        return Result.ok(notificationService.unreadCount(userId));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();
        notificationService.markRead(userId, id);
        return Result.ok();
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();
        notificationService.markAllRead(userId);
        return Result.ok();
    }
}
