package com.system.dto_vo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
@Data
public class PrescriptionCreateRequest {

    @NotEmpty(message = "处方药品不能为空")
    @Valid
    private List<PrescriptionItemRequest> items;


}
