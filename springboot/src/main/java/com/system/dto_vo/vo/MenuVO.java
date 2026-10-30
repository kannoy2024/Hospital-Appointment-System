package com.system.dto_vo.vo;

import com.system.entity.Menu;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.List;

@Data
public class MenuVO {

    private Long id;
    private String name;
    private String path;
    private String icon;
    private List<String> roleCodes;

    public static MenuVO fromEntity(Menu menu) {
        MenuVO vo = new MenuVO();
        BeanUtils.copyProperties(menu, vo);
        return vo;
    }


}
