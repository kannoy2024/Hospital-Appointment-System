package com.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
//菜单分类实体类
@Data
@TableName("sys_menu")
public class Menu {

    @TableId(type = IdType.AUTO)
    private Long id;
    //菜单父级id
    private Long parentId;
    //菜单名字
    private String name;
    //前端路由路径
    private String path;
    //图标名字
    private String icon;
    //排序id
    private Integer sortOrder;

    //角色
    @Deprecated
    private String roles;


}
