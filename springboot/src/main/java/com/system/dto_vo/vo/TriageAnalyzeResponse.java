package com.system.dto_vo.vo;

import lombok.Data;

import java.util.List;
@Data
//ai分诊分析回复
public class TriageAnalyzeResponse {

    private String source;
    private String summary;
    private String symptomAnalysis;
    private List<String> commonSigns;
    private String preliminaryConclusion;
    private String advice;
    private boolean urgent;
    private List<TriageDepartmentItem> departments;


}
