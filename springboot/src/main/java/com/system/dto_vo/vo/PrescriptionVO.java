package com.system.dto_vo.vo;

import com.system.entity.Prescription;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PrescriptionVO {

    private Long id;
    private Long appointmentId;
    private Long userId;
    private Long doctorId;
    private Long totalAmountFen;
    private String status;
    private String statusText;
    private String payMethod;
    private String payMethodText;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private List<PrescriptionItemVO> items;

    public static String statusLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "unpaid" -> "待支付";
            case "paid" -> "待取药";
            case "dispensed" -> "已取药";
            case "cancelled" -> "已取消";
            default -> status;
        };
    }

    public static PrescriptionVO fromEntity(Prescription entity) {
        PrescriptionVO vo = new PrescriptionVO();
        vo.setId(entity.getId());
        vo.setAppointmentId(entity.getAppointmentId());
        vo.setUserId(entity.getUserId());
        vo.setDoctorId(entity.getDoctorId());
        vo.setTotalAmountFen(entity.getTotalAmountFen());
        vo.setStatus(entity.getStatus());
        vo.setStatusText(statusLabel(entity.getStatus()));
        vo.setPayMethod(entity.getPayMethod());
        vo.setPayMethodText(AppointmentVO.payMethodLabel(entity.getPayMethod()));
        vo.setPayTime(entity.getPayTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }


}
