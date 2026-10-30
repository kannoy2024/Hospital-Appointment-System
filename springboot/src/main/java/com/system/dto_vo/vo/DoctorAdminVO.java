package com.system.dto_vo.vo;

import cn.hutool.core.bean.BeanUtil;
import com.system.entity.Doctor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoctorAdminVO {

    private Long id;
    private Long departmentId;
    private String departmentName;
    private String name;
    private String title;
    private Integer gender;
    private String avatar;
    private String intro;
    private Integer status;
    private LocalDateTime createTime;
    private Double rating;
    private Integer consultCount;
    private Integer reviewCount;
    private String loginUsername;

}
