package com.system.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NoArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 工具类
 * 提供 Web 应用开发中常用的工具方法，包括：
 *   获取当前请求对象 {@link #getRequest()}
 *   获取客户端真实 IP 地址 {@link #getClientIp()}
 *
 *
 */
@NoArgsConstructor
public final class WebUtils {

    /**
     * 获取当前线程绑定的 HTTP 请求对象
     * 原理：Spring Web 在处理请求时，会将 HttpServletRequest 绑定到当前线程的 ThreadLocal 中，
     * 任何地方（Controller、Service、工具类）都可以通过 RequestContextHolder 获取。
     *
     * @return 当前请求对象，如果当前线程不在 Web 请求上下文中则返回 null
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs == null) {
            return null;
        }

        return attrs.getRequest();
    }

    /**
     * 获取客户端真实 IP 地址
     * 为什么不能直接用 request.getRemoteAddr()
     * 当应用部署在 Nginx/Apache 等反向代理后面时，getRemoteAddr() 返回的是代理服务器的 IP，
     * 而不是客户端的真实 IP。代理服务器通常会在请求头中添加 X-Forwarded-For 字段，
     * 里面记录了真实的客户端 IP 地址。
     * IP 获取优先级：
     *   X-Forwarded-For 请求头（标准反向代理转发，取第一个 IP）
     *   request.getRemoteAddr()（直连或无代理时的兜底方案）
     *
     * @return 客户端真实 IP 地址，如果当前线程不在 Web 请求上下文中则返回 null
     */
    public static String getClientIp() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }

        String ip = request.getHeader("X-Forwarded-For");

        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}