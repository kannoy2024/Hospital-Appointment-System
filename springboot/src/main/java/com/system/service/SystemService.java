package com.system.service;

public interface SystemService {

    /**
     * 管理员设置系统状态
     * @param status 状态
     */
    void setStatus(Integer status);


    //获取当前系统状态
    String getStatus();
}
