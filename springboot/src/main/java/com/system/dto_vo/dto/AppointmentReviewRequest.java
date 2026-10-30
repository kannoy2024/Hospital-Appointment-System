package com.system.dto_vo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
@Data
public class AppointmentReviewRequest {

    @NotNull(message = "请选择评分")
    @Min(value = 1, message = "评分最低为1")
    @Max(value = 5, message = "评分最高为5")
    private Integer rating;

    private List<String> tags;

    @Size(max = 500, message = "评价内容不能超过500字")
    private String comment;


}
