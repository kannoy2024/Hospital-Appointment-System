package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.BusinessException;
import com.system.common.PageResult;
import com.system.dto_vo.vo.NotificationVO;
import com.system.entity.User;
import com.system.entity.UserNotification;
import com.system.mapper.UserMapper;
import com.system.mapper.UserNotificationMapper;
import com.system.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 站内消息服务实现：负责站内通知的写入、分页查询与已读标记，并支持按医生反查账号投递。
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final UserNotificationMapper notificationMapper;
    private final UserMapper userMapper;

    /**
     * 给指定用户写入一条站内通知。
     *
     * @param userId 接收用户ID（为空则直接返回）
     * @param type 通知类型
     * @param title 通知标题
     * @param content 通知内容
     * @param bizType 关联业务类型
     * @param bizId 关联业务ID
     */
    @Override
    @Transactional
    public void notifyUser(Long userId, String type, String title, String content, String bizType, Long bizId) {
        if (userId == null) {
            return;
        }
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setReadFlag(0);
        notificationMapper.insert(notification);
    }

    /**
     * 按医生ID反查其登录账号，向该医生投递站内通知。
     *
     * @param doctorId 医生ID（为空则直接返回）
     * @param type 通知类型
     * @param title 通知标题
     * @param content 通知内容
     * @param bizType 关联业务类型
     * @param bizId 关联业务ID
     */
    @Override
    @Transactional
    public void notifyDoctor(Long doctorId, String type, String title, String content, String bizType, Long bizId) {
        if (doctorId == null) {
            return;
        }
        User doctorUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getDoctorId, doctorId)
                .eq(User::getRole, "DOCTOR")
                .last("LIMIT 1"));
        if (doctorUser != null) {
            notifyUser(doctorUser.getId(), type, title, content, bizType, bizId);
        }
    }

    /**
     * 统计用户的未读通知数量。
     *
     * @param userId 用户ID
     * @return 未读通知数量
     */
    @Override
    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getReadFlag, 0));
        return count != null ? count : 0;
    }

    /**
     * 分页查询用户的站内通知列表。
     *
     * @param userId 用户ID
     * @param current 当前页码
     * @param size 每页条数
     * @param unreadOnly 是否只查未读
     * @return 通知分页结果
     */
    @Override
    public PageResult<NotificationVO> pageMine(Long userId, long current, long size, Boolean unreadOnly) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .orderByDesc(UserNotification::getCreateTime);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(UserNotification::getReadFlag, 0);
        }
        Page<UserNotification> page = notificationMapper.selectPage(new Page<>(current, size), wrapper);
        List<NotificationVO> records = page.getRecords().stream()
                .map(NotificationVO::fromEntity)
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 将单条通知标记为已读，并校验该通知归属当前用户。
     *
     * @param userId 用户ID
     * @param notificationId 通知ID
     */
    @Override
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getId, notificationId)
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getReadFlag, 0)
                .set(UserNotification::getReadFlag, 1));
        if (updated == 0) {
            UserNotification existing = notificationMapper.selectById(notificationId);
            if (existing == null || !existing.getUserId().equals(userId)) {
                throw new BusinessException(403, "无权操作该消息");
            }
        }
    }

    /**
     * 将用户的所有未读通知批量标记为已读。
     *
     * @param userId 用户ID
     */
    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getReadFlag, 0)
                .set(UserNotification::getReadFlag, 1));
    }
}
