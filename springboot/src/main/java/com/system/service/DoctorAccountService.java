package com.system.service;

import com.system.entity.Doctor;

import java.util.Collection;
import java.util.Map;

public interface DoctorAccountService {

    /** 创建医生登录账号，返回用户名；已存在则返回现有用户名 */
    String createAccount(Doctor doctor);

    /** 无账号时补建（编辑旧数据等场景） */
    String ensureAccount(Doctor doctor);

    void syncNickname(Doctor doctor);

    /** 删除单个医生账号 */
    void deleteByDoctorId(Long doctorId);

    /* 根据医生ID获取登录用户名*/
    String getUsernameByDoctorId(Long doctorId);

    /** 批量根据医生ID获取登录用户名，key=医生ID，value=登录用户名 */
    Map<Long, String> getUsernamesByDoctorIds(Collection<Long> doctorIds);
}
