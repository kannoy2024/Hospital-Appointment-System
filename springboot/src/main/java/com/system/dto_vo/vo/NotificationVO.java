package com.system.dto_vo.vo;

import com.system.entity.UserNotification;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class NotificationVO {

    private Long id;
    private String type;
    private String typeText;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Boolean read;
    private LocalDateTime createTime;

    public static NotificationVO fromEntity(UserNotification entity) {
        NotificationVO vo = new NotificationVO();
        vo.setId(entity.getId());
        vo.setType(entity.getType());
        vo.setTypeText(typeLabel(entity.getType()));
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setRead(entity.getReadFlag() != null && entity.getReadFlag() == 1);
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    public static String typeLabel(String type) {
        if (type == null) {
            return "系统通知";
        }
        return switch (type) {
            case "appointment_paid" -> "预约成功";
            case "appointment_cancelled" -> "预约取消";
            case "appointment_expired" -> "预约超时";
            case "check_in_success" -> "到院签到";
            case "patient_checked_in" -> "患者到院";
            case "consultation_started" -> "开始接诊";
            case "prescription_unpaid" -> "待缴药费";
            case "prescription_paid" -> "药费已付";
            case "prescription_paid_doctor" -> "患者已缴费";
            case "prescription_dispensed" -> "取药完成";
            default -> "系统通知";
        };
    }

}
