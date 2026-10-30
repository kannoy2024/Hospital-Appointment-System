package com.system.dto_vo.vo;

import com.system.entity.Menu;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.List;

@Data
public class MenuAdminVO {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String icon;
    private Integer sortOrder;
    /** 历史字段（sys_menu.roles 逗号串），已由 roleCodes 取代，仅作过渡保留 */
    private String roles;
    private List<String> roleCodes;

    public static MenuAdminVO fromEntity(Menu menu) {
        MenuAdminVO vo = new MenuAdminVO();
        BeanUtils.copyProperties(menu, vo);
        return vo;
    }


}
