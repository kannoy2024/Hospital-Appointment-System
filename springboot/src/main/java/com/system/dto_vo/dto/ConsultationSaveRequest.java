package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

//问诊存档
@Data
public class ConsultationSaveRequest {

    @NotBlank(message = "主诉不能为空")
    private String chiefComplaint;

    private String presentIllness;

    @NotBlank(message = "诊断不能为空")
    private String diagnosis;

    private String treatmentPlan;

    private String doctorAdvice;

}
