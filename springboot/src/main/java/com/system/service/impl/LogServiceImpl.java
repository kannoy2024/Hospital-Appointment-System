package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.BusinessException;
import com.system.common.PageResult;
import com.system.common.WebUtils;
import com.system.dto_vo.vo.SystemLogVO;
import com.system.entity.LoginLog;
import com.system.entity.OperationLog;
import com.system.mapper.LoginLogMapper;
import com.system.mapper.OperationLogMapper;
import com.system.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日志服务实现：负责登录日志与操作日志的写入、分页查询与批量删除。
 */
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;


    /**
     * 写入一条登录日志。
     *
     * @param username 登录用户名
     * @param success 是否登录成功
     * @param message 结果描述
     */
    @Override
    public void saveLoginLog(String username, boolean success, String message) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setIp(WebUtils.getClientIp());
        log.setStatus(success ? 1 : 0);
        log.setMessage(message);
        log.setCreateTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }


    /**
     * 写入一条操作日志，并从当前请求上下文捕获请求方法与地址。
     *
     * @param username 操作用户名
     * @param module 业务模块
     * @param action 操作动作
     * @param success 是否成功
     */
    @Override
    public void saveOperationLog(String username,
                                 String module,
                                 String action,
                                 boolean success) {

        OperationLog log = new OperationLog();
        log.setUsername(username);
        log.setModule(module);
        log.setAction(action);
        log.setStatus(success ? 1 : 0);

        // 从当前请求捕获 method / url（异常处理器里依然能拿到请求上下文）
        HttpServletRequest request = WebUtils.getRequest();
        if (request != null) {
            log.setMethod(request.getMethod());    // GET / POST / PUT / DELETE
            log.setUrl(request.getRequestURI());   // 如 /api/doctor/appointments
        }
        log.setIp(WebUtils.getClientIp());
        log.setCostMs(0);                          // 异常场景无耗时，填 0
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    /**
     * 分页查询系统日志，按类型分发到操作日志或登录日志。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param username 用户名筛选（可为空）
     * @param type 日志类型（operation-操作日志，其他-登录日志）
     * @return 日志分页结果
     */
    @Override
    public PageResult<SystemLogVO> pageSystemLogs(long current, long size, String username, String type) {
        if ("operation".equalsIgnoreCase(type)) {
            return pageOperation(current, size, username);
        }
        return pageLogin(current, size, username);
    }

    /**
     * 分页查询登录日志，可按用户名模糊筛选。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param username 用户名筛选（可为空）
     * @return 日志分页结果
     */
    private PageResult<SystemLogVO> pageLogin(long current, long size, String username) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(LoginLog::getUsername, username);
        }
        wrapper.orderByDesc(LoginLog::getCreateTime);
        Page<LoginLog> page = loginLogMapper.selectPage(new Page<>(current, size), wrapper);
        List<SystemLogVO> records = page.getRecords().stream()
                .map(SystemLogVO::fromLogin)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 分页查询操作日志，可按用户名模糊筛选。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param username 用户名筛选（可为空）
     * @return 日志分页结果
     */
    private PageResult<SystemLogVO> pageOperation(long current, long size, String username) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(OperationLog::getUsername, username);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        Page<OperationLog> page = operationLogMapper.selectPage(new Page<>(current, size), wrapper);
        List<SystemLogVO> records = page.getRecords().stream()
                .map(SystemLogVO::fromOperation)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 按类型批量删除日志（去重后删除），类型无效或未选记录时抛出业务异常。
     *
     * @param type 日志类型（operation-操作日志，login-登录日志）
     * @param ids 待删除的日志ID列表
     */
    @Override
    public void deleteLogs(String type, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的日志");
        }
        //去重[1,2,3,4,4,4......]使用stream流式处理全部待删除的id
        List<Long> distinct = ids.stream().distinct().toList();
        if ("operation".equalsIgnoreCase(type)) {
            operationLogMapper.deleteBatchIds(distinct);
        } else if ("login".equalsIgnoreCase(type)) {
            loginLogMapper.deleteBatchIds(distinct);
        } else {
            throw new BusinessException("日志类型无效");
        }
    }
}
