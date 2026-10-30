package com.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("prescription_item")
public class PrescriptionItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long prescriptionId;
    private Long medicineId;
    private String medicineName;
    private String spec;
    private String dosage;
    private String frequency;
    private Integer days;
    private Integer quantity;

    @TableField("unit_price_fen")
    private long unitPriceFen;

    @TableField("amount_fen")
    private long amountFen;

}
