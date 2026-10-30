package com.system.dto_vo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AppointmentVO {

    private Long id;
    private Long scheduleId;
    private Long userId;
    private Long patientId;
    private LocalDate scheduleDate;
    private String timeSlot;
    private String doctorName;
    private String doctorTitle;
    private Long doctorId;
    private String departmentName;
    private String patientName;
    private String patientPhone;
    private Long amountFen;
    private String payMethod;
    private String payMethodText;
    private LocalDateTime payTime;
    private String status;
    private Boolean evaluated;
    private Long prescriptionId;
    private String prescriptionStatus;
    private Long prescriptionAmountFen;
    private Boolean hasConsultation;
    private String remark;
    private LocalDateTime createTime;

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
