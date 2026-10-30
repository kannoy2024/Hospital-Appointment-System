package com.system.dto_vo.vo;

import com.system.entity.Department;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class DepartmentVO {

    private Long id;
    private String name;
    private String description;
    private String location;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;

    public static DepartmentVO fromEntity(Department entity) {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setLocation(entity.getLocation());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }


}
