package com.system.dto_vo.vo;

import lombok.Data;

@Data
public class ClinicalBundleVO {

    private AppointmentVO appointment;
    private ConsultationRecordVO consultation;
    private PrescriptionVO prescription;


}
