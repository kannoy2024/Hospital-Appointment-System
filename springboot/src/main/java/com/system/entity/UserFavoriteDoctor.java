package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.lang.reflect.Type;
import java.time.LocalDateTime;

@Data
@TableName("user_favorite_doctor")
public class UserFavoriteDoctor {


    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long doctorId;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
