package com.system.common;

import com.system.service.LogService;
import io.lettuce.core.RedisConnectionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.PoolException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.stream.Collectors;

import static com.system.common.MessageConstants.*;
import static com.system.security.SecurityUtils.getCurrentUser;

/**
 * 全局异常处理：业务异常、校验失败、权限不足、数据库连接等统一返回 Result JSON。
 * <p>
 * 功能说明：
 * 1. 拦截所有Controller层抛出的异常
 * 2. 统一转换为标准的Result<T> JSON响应格式
 * 3. 根据异常类型设置不同的HTTP状态码
 * 4. 提供友好的错误提示信息
 */
@RestControllerAdvice  // 标记为全局异常处理器，自动扫描所有Controller
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    // 日志记录器，用于记录未处理的异常信息
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final LogService logService;

    /**
     * 处理业务异常（自定义异常）
     *
     * @param e 业务异常对象
     * @return 包含错误码和错误信息的Result对象
     *
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        logOperationLog("业务异常", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid校验失败）
     *
     * @param e 方法参数校验异常
     * @return 包含所有字段错误信息的Result对象
     * <p>
     * 使用场景：请求参数不符合校验规则，如：邮箱格式错误、字段为空等
     * HTTP状态码：400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)  // 设置HTTP状态码为400
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        // 收集所有字段的错误信息，用逗号拼接
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)  // 获取校验注解中的message属性
                .collect(Collectors.joining(", "));
        logOperationLog("参数异常", e.getMessage());
        return Result.fail(400, message);
    }

    /**
     * 处理认证失败异常（用户名或密码错误）
     *
     * @param e 凭证错误异常
     * @return 统一的认证失败提示
     * <p>
     * 使用场景：用户登录时用户名或密码错误
     * HTTP状态码：401 Unauthorized
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        logOperationLog("登录异常", "登录认证失败");
        return Result.fail(401, ACCOUNT_PASSWORD_ERROR);
    }

    // 认证相关异常

    /**
     * 处理账号禁用异常
     *
     * @param e 禁用异常
     * @return 账号禁用提示
     * <p>
     * 使用场景：用户账号被管理员禁用后尝试登录
     * HTTP状态码：401 Unauthorized
     */
    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleDisabled(DisabledException e) {
        logOperationLog("登录异常", "被禁用账号试图登录");
        return Result.fail(401, ACCOUNT_LOCKED);
    }

    /**
     * 处理账号锁定异常
     *
     * @param e 账号锁定异常
     * @return 账号锁定提示
     * <p>
     * 使用场景：用户账号被锁定（status=0）时尝试登录
     * HTTP状态码：401 Unauthorized
     */
    @ExceptionHandler(LockedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleLocked(LockedException e) {
        logOperationLog("登录异常", "被锁定账号试图登录");
        return Result.fail(401, ACCOUNT_LOCKED);
    }

    /**
     * 处理权限不足异常
     *
     * @param e 访问拒绝异常
     * @return 无权限提示
     * <p>
     * 使用场景：用户尝试访问未授权的资源
     * HTTP状态码：403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        logOperationLog("权限管理", "访问被拒绝");
        return Result.fail(403, INSUFFICIENT_PERMISSIONS);
    }


    //  数据库异常

    /**
     * 处理数据访问异常（数据库相关）
     *
     * @param e 数据访问异常
     * @return 数据库连接失败提示
     * <p>
     * 使用场景：数据库连接失败、SQL执行错误等
     * HTTP状态码：503 Service Unavailable
     * <p>
     * 注意：会进一步判断是否因账号密码错误导致的连接失败
     */
    @ExceptionHandler({
            CannotGetJdbcConnectionException.class,  // 无法获取JDBC连接
            DataAccessException.class  // 数据访问异常（通用）
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleDataAccess(Exception e) {
        // 检查是否因访问被拒绝（账号密码错误）
        if (isAccessDenied(e)) {
            return Result.fail(503, DB_ERROR_HINT + SQL_LOGIN_ERROR);
        }
        // 记录未处理的异常日志，方便排查问题
        log.error("数据库异常", e);
        return Result.fail(503, DB_ERROR_HINT);
    }

    /**
     * 处理内部认证服务异常
     *
     * @param e 内部认证服务异常
     * @return 根据异常原因返回不同提示
     * <p>
     * 使用场景：Spring Security认证过程中发生的内部异常
     * HTTP状态码：503 Service Unavailable
     * <p>
     * 说明：InternalAuthenticationServiceException通常包装了底层异常，
     * 需要递归检查是否包含数据库相关异常
     */
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleInternalAuth(InternalAuthenticationServiceException e) {
        // 检查异常链中是否包含数据库错误
        if (containsDbError(e)) {
            if (isAccessDenied(e)) {
                return Result.fail(503, DB_ERROR_HINT + SQL_LOGIN_ERROR);
            }
            return Result.fail(503, DB_ERROR_HINT);
        }
        // 非数据库相关的认证异常，返回500
        return Result.fail(500, "认证服务异常");
    }


    /**
     * 检查异常链中是否包含数据库相关异常
     *
     * @param e 需要检查的异常对象
     * @return true：包含数据库异常；false：不包含
     * 实现方式：递归遍历异常链（通过 getCause()）
     * 检查类型：CannotGetJdbcConnectionException、DataAccessException、SQLException
     * 使用场景：Spring Security的异常可能包装了底层数据库异常，
     * 需要深入检查被包装的原始异常
     */
    private boolean containsDbError(Throwable e) {
        while (e != null) {
            if (e instanceof CannotGetJdbcConnectionException
                    || e instanceof DataAccessException
                    || e instanceof SQLException) {
                return true;
            }
            e = e.getCause();  // 获取被包装的下一层异常
        }
        return false;
    }

    /**
     * 检查异常链中是否为MySQL访问拒绝错误
     *
     * @param e 需要检查的异常对象
     * @return true：账号密码错误导致的访问拒绝；false：其他原因
     * 实现方式：递归遍历异常链，查找包含 "Access denied" 消息的SQLException
     * <p>
     * 使用场景：区分数据库连接失败的具体原因（账号密码错误 vs 其他网络问题）
     */
    private boolean isAccessDenied(Throwable e) {
        while (e != null) {
            // 检查是否为SQLException且消息包含"Access denied"
            if (e instanceof SQLException sql && sql.getMessage() != null
                    && sql.getMessage().contains("Access denied")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    /**
     * 处理Redis连接异常
     */
    @ExceptionHandler({
            RedisConnectionFailureException.class,
            PoolException.class,
            RedisConnectionException.class
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleRedisConnectionException(Exception e) {
        logOperationLog("Redis异常", "Redis连接失败: " + e.getMessage());

        // 可以进一步区分不同的Redis异常类型
        if (e instanceof RedisConnectionFailureException) {
            log.error("Redis连接失败，请检查Redis服务是否正常运行: {}", e.getMessage());
            return Result.fail(503, "Redis服务不可用，请稍后重试或联系管理员");
        }

        if (e instanceof PoolException) {
            log.error("Redis连接池获取资源失败: {}", e.getMessage());
            return Result.fail(503, "Redis连接池资源耗尽，请稍后重试");
        }

        return Result.fail(503, "缓存服务暂时不可用");
    }

    // 未知异常

    /**
     * 处理所有未捕获的异常（兜底处理器）
     *
     * @param e 未知异常
     * @return 统一的服务器错误提示
     * <p>
     * 使用场景：所有未被上面处理器匹配的异常
     * HTTP状态码：500 Internal Server Error
     * <p>
     * 注意：会记录错误日志便于排查，同时也会检测是否为数据库相关异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        // 检查是否为数据库异常
        if (containsDbError(e)) {
            if (isAccessDenied(e)) {
                return Result.fail(503, DB_ERROR_HINT + SQL_LOGIN_ERROR);
            }
            return Result.fail(503, DB_ERROR_HINT);
        }
        // 记录未处理的异常日志，方便排查问题
        log.error("未处理异常", e);
        return Result.fail(UNKNOWN_ERROR);
    }


    /**
     * 记录安全异常的操作日志（统一入口）
     * <p>
     * 封装了"安全获取操作人 + 获取请求路径 + 写入操作日志"，
     * 供所有全局异常处理器复用，避免每个异常处理器重复写 try-catch。
     *
     * @param module 归属模块，如"权限管理"、"系统异常"
     * @param action 操作描述，如"访问被拒绝"、"未知异常"
     */
    private void logOperationLog(String module, String action) {
        try {
            //1 尝试安全获取当前用户名
            String username;
            try {
                username = getCurrentUser().getUsername();
            } catch (Exception ex) {
                username = "匿名";
            }
            //2 获取被访问的接口路径，使用三元表达式判断URL是否为空
            String url = WebUtils.getRequest() != null ? WebUtils.getRequest().getRequestURI() : "未知";

            //3 记录当前日志
            logService.saveOperationLog(username, module, action + ":" + url, false);
        } catch (Exception logEx) {
            log.warn("记录操作日志失败:{}", logEx.getMessage());
        }
    }
}