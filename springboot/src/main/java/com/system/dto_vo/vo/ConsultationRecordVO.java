package com.system.dto_vo.vo;

import com.system.entity.ConsultationRecord;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
@Data
public class ConsultationRecordVO {

    private Long id;
    private Long appointmentId;
    private Long doctorId;
    private String chiefComplaint;
    private String presentIllness;
    private String diagnosis;
    private String treatmentPlan;
    private String doctorAdvice;
    private LocalDateTime createTime;

    public static ConsultationRecordVO fromEntity(ConsultationRecord entity) {
        ConsultationRecordVO vo = new ConsultationRecordVO();
        BeanUtils.copyProperties(entity,vo);
        return vo;
    }

}
