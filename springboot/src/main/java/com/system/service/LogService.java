package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.vo.SystemLogVO;

import java.util.List;

/**
 * 日志服务：登录日志与操作日志的记录、查询、删除。
 */
public interface LogService {

    /** 记录一条登录日志（成功/失败） */
    void saveLoginLog(String username, boolean success, String message);

    /**
     * 记录操作日志
     * @param username 操作人
     * @param module   所属模块，如"权限管理"、"排班管理"
     * @param action   操作描述，如"访问被拒绝: /api/doctor/appointments"
     * @param success  是否成功（权限不足=false）
     */
    void saveOperationLog(String username, String module, String action, boolean success);

    /**
     * 分页查询系统日志（登录/操作日志统一视图）
     * @param type login / operation，为空或 all 时默认 login（由前端传具体类型）
     */
    PageResult<SystemLogVO> pageSystemLogs(long current, long size, String username, String type);

    /** 批量删除日志（按类型） */
    void deleteLogs(String type, List<Long> ids);
}
