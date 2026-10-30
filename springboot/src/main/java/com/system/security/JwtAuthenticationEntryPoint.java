package com.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 认证入口点（Authentication Entry Point）
 * <p>
 * 这是 Spring Security 中处理"未认证"情况的统一入口。
 * <p>
 * 当用户访问需要认证的资源（如 @PreAuthorize 保护的接口），
 * 但请求头中没有携带有效的 JWT 令牌时，Spring Security 会触发此组件。
 * <p>
 * 触发场景：
 * <ul>
 *   <li>请求头中没有 Authorization 字段</li>
 *   <li>Authorization 字段格式错误（如没有 Bearer 前缀）</li>
 *   <li>JWT 令牌无效（签名错误、已过期、格式错误）</li>
 *   <li>用户未登录</li>
 * </ul>
 * <p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * Jackson JSON 序列化器
     * 用于将 Result 对象转换为 JSON 字符串返回给客户端
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理未认证请求
     * <p>
     * 当 Spring Security 检测到请求未认证时，会调用此方法。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>设置 HTTP 状态码为 401（UNAUTHORIZED）</li>
     *   <li>设置响应内容类型为 application/json</li>
     *   <li>设置字符编码为 UTF-8</li>
     *   <li>构建统一的 Result 对象（code=401, message="未登录或登录已过期"）</li>
     *   <li>将 Result 对象序列化为 JSON 字符串</li>
     *   <li>通过 HttpServletResponse 的 Writer 输出</li>
     * </ol>
     * <p>
     * @param request          HTTP 请求对象
     * @param response         HTTP 响应对象
     * @param authException    认证异常（包含具体错误信息）
     *                         Spring Security 会传入具体异常，如：
     *                         - BadCredentialsException（密码错误）
     *                         - DisabledException（账号被禁用）
     *                         - 但这里我们不使用它，只返回统一消息
     * @throws IOException     IO 异常（写响应时可能抛出）
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // 1 设置 HTTP 状态码
        // SC_UNAUTHORIZED = 401
        // 表示"未认证"，即用户未登录或登录已过期
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 2 设置响应头
        // 告诉客户端返回的是 JSON 数据
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // 设置字符编码为 UTF-8，确保中文不乱码
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // 3 构建统一响应体
        // 使用项目统一的 Result 格式
        // code=401, message="未登录或登录已过期"
        Result<Void> result = Result.fail(401, "未登录或登录已过期");

        //  4 序列化为 JSON 并输出
        // objectMapper.writeValueAsString() 将 Java 对象转为 JSON 字符串
        // response.getWriter().write() 将字符串写入响应体
        response.getWriter().write(objectMapper.writeValueAsString(result));

        // Spring 会在最后自动 flush
    }
}