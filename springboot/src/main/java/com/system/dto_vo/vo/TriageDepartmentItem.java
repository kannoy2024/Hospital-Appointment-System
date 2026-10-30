package com.system.dto_vo.vo;

import lombok.Data;

import java.util.List;

//分诊科室
@Data
public class TriageDepartmentItem {

    private Long id;
    private String name;
    private String description;
    private Integer score;
    private String reason;
    private List<String> symptoms;


}
