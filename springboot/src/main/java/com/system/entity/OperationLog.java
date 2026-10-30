package com.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("sys_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    //模块
    private String module;
    //操作
    private String action;
    //请求方式
    private String method;
    //请求路径
    private String url;
    //请求ip地址
    private String ip;
    //耗时毫秒
    private Integer costMs;
    //状态
    private Integer status;

    private LocalDateTime createTime;


}
