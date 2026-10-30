package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.vo.NotificationVO;

/**
 * 站内通知服务：用户/医生消息的发送、查询、已读管理。
 */
public interface NotificationService {

    /** 给指定用户发一条站内通知 */
    void notifyUser(Long userId, String type, String title, String content, String bizType, Long bizId);

    /** 给指定医生发通知（通过 doctorId 反查医生账号） */
    void notifyDoctor(Long doctorId, String type, String title, String content, String bizType, Long bizId);

    /** 查询用户未读消息数 */
    long unreadCount(Long userId);

    /** 分页查询用户消息，可按未读筛选 */
    PageResult<NotificationVO> pageMine(Long userId, long current, long size, Boolean unreadOnly);

    /** 标记单条消息已读 */
    void markRead(Long userId, Long notificationId);

    /** 标记全部消息已读 */
    void markAllRead(Long userId);
}
