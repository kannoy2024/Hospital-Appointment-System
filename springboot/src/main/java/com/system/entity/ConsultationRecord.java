package com.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("consultation_record")
public class ConsultationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;//问诊病例id
    private Long appointmentId;//预约记录id
    private Long doctorId;//医生id
    private String chiefComplaint;//主诉
    private String presentIllness;//先病史
    private String diagnosis;//诊断
    private String treatmentPlan;//治疗方案
    private String doctorAdvice;//医嘱

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
