package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
//ai分诊分析请求
public class TriageAnalyzeRequest {

    @NotBlank(message = "请描述您的症状")
    @Size(max = 200, message = "症状描述不能超过200字")
    private String symptom;

}
