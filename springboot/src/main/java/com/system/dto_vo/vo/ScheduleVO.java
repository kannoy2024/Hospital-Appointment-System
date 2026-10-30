package com.system.dto_vo.vo;

import com.system.entity.Schedule;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ScheduleVO {

    private Long id;
    private Long doctorId;
    private String doctorName;
    private String departmentName;
    private LocalDate scheduleDate;
    private String timeSlot;
    private Integer totalQuota;
    private Integer bookedCount;
    private Integer remainQuota;
    private Integer status;
    private LocalDateTime createTime;

    public static ScheduleVO fromEntity(Schedule entity, String doctorName, String departmentName) {
        ScheduleVO vo = new ScheduleVO();
        vo.setId(entity.getId());
        vo.setDoctorId(entity.getDoctorId());
        vo.setDoctorName(doctorName);
        vo.setDepartmentName(departmentName);
        vo.setScheduleDate(entity.getScheduleDate());
        vo.setTimeSlot(entity.getTimeSlot());
        vo.setTotalQuota(entity.getTotalQuota());
        vo.setBookedCount(entity.getBookedCount());
        vo.setRemainQuota(entity.getTotalQuota() - entity.getBookedCount());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }


}