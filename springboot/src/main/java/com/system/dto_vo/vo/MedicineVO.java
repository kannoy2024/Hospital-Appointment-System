package com.system.dto_vo.vo;

import com.system.entity.Medicine;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class MedicineVO {

    private Long id;
    private String name;
    private String spec;
    private String unit;
    private Long priceFen;
    private Integer stock;
    private String usageDesc;
    private Integer status;
    private LocalDateTime createTime;

    public static MedicineVO fromEntity(Medicine medicine) {
        MedicineVO vo = new MedicineVO();
        BeanUtils.copyProperties(medicine, vo);
        return vo;
    }

}
