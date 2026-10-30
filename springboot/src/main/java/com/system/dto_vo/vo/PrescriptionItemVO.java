package com.system.dto_vo.vo;

import com.system.entity.PrescriptionItem;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class PrescriptionItemVO {

    private Long id;
    private Long medicineId;
    private String medicineName;
    private String spec;
    private String dosage;
    private String frequency;
    private Integer days;
    private Integer quantity;
    private Long unitPriceFen;
    private Long amountFen;

    public static PrescriptionItemVO fromEntity(PrescriptionItem prescriptionItem) {
        PrescriptionItemVO vo = new PrescriptionItemVO();
        BeanUtils.copyProperties(prescriptionItem, vo);
        return vo;
    }

}
