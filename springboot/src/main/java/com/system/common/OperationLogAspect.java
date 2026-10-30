package com.system.common;

import com.system.entity.OperationLog;
import com.system.mapper.OperationLogMapper;
import com.system.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作日志切面（AOP）
 *
 * <h3>作用</h3>
 * 拦截所有标注了 {@link Log} 注解的 Controller 方法，自动记录操作日志到
 * {@code sys_operation_log} 表。
 *
 * <h3>使用方式</h3>
 * 在 Controller 方法上加一行即可：
 * <pre>{@code
 * @Log(module = "用户管理", action = "新增用户")
 * @PostMapping
 * public Result<?> createUser(...) { ... }
 * }</pre>
 *
 * <h3>拦截点</h3>
 * 基于 {@code @Around} 环绕通知，在整个方法执行前后都能够操作日志对象，
 * 无论方法正常返回还是抛出异常都会记录（通过 {@code finally} 块保证）。
 *
 * <h3>记录内容</h3>
 * 每条日志包含：操作模块、动作描述、请求用户、HTTP 方法和 URL、
 * 客户端 IP、执行耗时（毫秒）、执行结果（成功/失败）、创建时间。
 *
 * @see Log              自定义注解，定义模块名和动作名
 * @see OperationLog     数据库实体，对应 sys_operation_log 表
 * @see SecurityUtils    从 SecurityContext 中获取当前登录用户
 * @see WebUtils         获取当前请求和客户端真实 IP
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    // 建议在类里加一个独立 Logger（原因见下方⚠️）
    private static final Logger LOG = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogMapper operationLogMapper;  // 操作日志表的 Mapper，负责写入数据库

    /**
     * 环绕通知：在目标方法执行前后记录操作日志
     *
     * @param point   切入点对象，通过 {@code point.proceed()} 执行目标方法
     * @param logAnno 目标方法上的 {@code @Log} 注解实例，从中读取 module 和 action
     * @return 目标方法的原始返回值（原样返回，不影响调用方）
     * @throws Throwable 目标方法抛出的异常（原样上抛，不吞异常）
     */
    @Around("@annotation(logAnno)")  // 拦截所有带 @Log 注解的方法
    public Object around(ProceedingJoinPoint point, Log logAnno) throws Throwable {

        //  一、请求进入时：初始化日志对象并记录请求信息 

        long start = System.currentTimeMillis();            // 记录方法开始时间，用于计算耗时

        OperationLog log = new OperationLog();              // 创建日志实体
        log.setModule(logAnno.module());                   // 从注解中取模块名（如"用户管理"）
        log.setAction(logAnno.action());                   // 从注解中取动作名（如"新增用户"）
        log.setCreateTime(LocalDateTime.now());            // 操作时间 = 当前时间
        log.setIp(WebUtils.getClientIp());                 // 从 HTTP 请求中解析客户端真实 IP
        // （优先取 X-Forwarded-For，其次 remoteAddr）

        // 填充 HTTP 方法和请求路径（如 GET /api/users）
        HttpServletRequest request = WebUtils.getRequest();
        if (request != null) {
            log.setMethod(request.getMethod());             // GET / POST / PUT / DELETE
            log.setUrl(request.getRequestURI());            // 例如 /api/admin/users
        }

        // 填充操作人：从 JWT SecurityContext 中取当前登录用户名
        try {
            log.setUsername(SecurityUtils.getCurrentUserEntity().getUsername());
        } catch (Exception ignored) {
            // 如果当前请求未登录（如公开接口也意外加了 @Log 注解），
            // 获取用户信息会抛异常，此时标记为"匿名"
            log.setUsername("匿名");
        }

        //  二、执行目标方法并区分成功/失败 

        try {
            // 2a. 执行真正的业务方法（如 Controller 中的 createUser）
            Object result = point.proceed();

            // 2b. 方法正常返回 → 标记为成功
            log.setStatus(1);
            return result;                                  // 原样返回，不改变调用方的结果

        } catch (Throwable e) {
            // 2c. 方法抛出异常 → 标记为失败，追加失败标识到动作名
            log.setStatus(0);
            log.setAction(log.getAction() + "（失败）");
            throw e;                                        // 原样抛出，不吞异常（交给 GlobalExceptionHandler 处理）

        } finally {
            //  三、无论成功还是失败，最终都要记录耗时并写入数据库 

            // 计算耗时（毫秒）
            log.setCostMs((int) (System.currentTimeMillis() - start));
            try {
                // 写入 sys_operation_log 表
                // 注意：这里直接调 Mapper 而非 Service，因为操作日志本身不应再触发 AOP
                operationLogMapper.insert(log);
            } catch (Exception ex) {
                // 日志写失败 → 只记 WARN，绝不上抛
                LOG.warn("操作日志写入失败，已忽略：module={}, action={}", log.getModule(), log.getAction(), ex);
            }

        }
    }
}
