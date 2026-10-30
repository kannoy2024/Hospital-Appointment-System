package com.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("appointment_review")
public class AppointmentReview {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appointmentId;
    private Long userId;
    private Long doctorId;
    private Integer rating;
    private String tags;
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
