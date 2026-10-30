package com.system.dto_vo.dto;

import com.system.entity.Medicine;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MedicineSaveRequest {

    private Long id;

    @NotBlank(message = "药品名称不能为空")
    private String name;

    private String spec;

    private String unit = "盒";

    @NotNull(message = "单价不能为空")
    @Min(value = 0, message = "单价不能为负")
    private Long priceFen;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    private String usageDesc;

    @NotNull(message = "状态不能为空")
    private Integer status;


    public Medicine toEntity() {
        Medicine m = new Medicine();
        m.setId(id);
        m.setName(name.trim());
        m.setSpec(spec);
        m.setUnit(unit != null ? unit.trim() : "盒");
        m.setPriceFen(priceFen);
        m.setStock(stock);
        m.setUsageDesc(usageDesc);
        m.setStatus(status);
        return m;
    }
}
