package com.system.dto_vo.vo;

import com.system.entity.LoginLog;
import com.system.entity.OperationLog;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class SystemLogVO {

    private Long id;
    /** LOGIN / OPERATION */
    private String logType;
    private String logTypeName;
    private String username;
    private String module;
    private String action;
    private String method;
    private String url;
    private String ip;
    private Integer costMs;
    private Integer status;
    private String message;
    private LocalDateTime createTime;

    public static SystemLogVO fromLogin(LoginLog log) {
        SystemLogVO vo = new SystemLogVO();
        vo.setId(log.getId());
        vo.setLogType("LOGIN");
        vo.setLogTypeName("登录");
        vo.setUsername(log.getUsername());
        vo.setIp(log.getIp());
        vo.setStatus(log.getStatus());
        vo.setMessage(log.getMessage());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    public static SystemLogVO fromOperation(OperationLog log) {
        SystemLogVO vo = new SystemLogVO();
        vo.setId(log.getId());
        vo.setLogType("OPERATION");
        vo.setLogTypeName("操作");
        vo.setUsername(log.getUsername());
        vo.setModule(log.getModule());
        vo.setAction(log.getAction());
        vo.setMethod(log.getMethod());
        vo.setUrl(log.getUrl());
        vo.setIp(log.getIp());
        vo.setCostMs(log.getCostMs());
        vo.setStatus(log.getStatus());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }


}
