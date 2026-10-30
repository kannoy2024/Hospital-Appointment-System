package com.system.dto_vo.vo;

import com.system.entity.Doctor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DoctorVO {

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

    public static DoctorVO fromEntity(Doctor entity, String departmentName) {
        DoctorVO vo = new DoctorVO();
        vo.setId(entity.getId());
        vo.setDepartmentId(entity.getDepartmentId());
        vo.setDepartmentName(departmentName);
        vo.setName(entity.getName());
        vo.setTitle(entity.getTitle());
        vo.setGender(entity.getGender());
        vo.setAvatar(entity.getAvatar());
        vo.setIntro(entity.getIntro());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

}
