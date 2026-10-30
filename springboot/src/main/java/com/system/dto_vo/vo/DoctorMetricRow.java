package com.system.dto_vo.vo;

import lombok.Data;

@Data
public class DoctorMetricRow {

    private Long doctorId;
    private Double avgRating;
    private Long reviewCount;
    private Long consultCount;

}
