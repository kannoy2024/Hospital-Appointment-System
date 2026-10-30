package com.system.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * <p>
 * 这是 Spring Security 过滤器链中的核心组件，负责：
 * <ul>
 *   <li>从请求头中提取 JWT 令牌</li>
 *   <li>验证令牌的有效性（签名、过期时间）</li>
 *   <li>将有效的用户信息注入到 Spring Security 的上下文中</li>
 * </ul>
 * <p>
 * 执行时机：在 Controller 方法执行之前，Spring Security 的认证流程中。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>每个请求到达时，先经过此过滤器</li>
 *   <li>从 Authorization 请求头提取 Token</li>
 *   <li>验证 Token 是否有效</li>
 *   <li>如果有效，加载用户信息并存入 SecurityContext</li>
 *   <li>后续的 @PreAuthorize 和 SecurityUtils 就能直接使用用户信息</li>
 * </ol>
 * <p>
 * 继承 OncePerRequestFilter 确保每个请求只执行一次，
 * 避免在过滤器链中被多次调用。
 *
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    //JWT 令牌工具类：负责签发、验证、解析令牌
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户详情服务：根据用户名加载用户信息
     * <p>
     * 由 Spring Security 的 UserDetailsService 接口实现
     */
    private final UserDetailsService userDetailsService;


    /**
     * 每个请求都会经过此方法（继承自 OncePerRequestFilter）
     * <p>
     * 执行流程：
     * <ol>
     *   <li>从请求头中提取 JWT 令牌</li>
     *   <li>验证令牌是否有效（签名 + 过期时间）</li>
     *   <li>如果有效，从令牌中提取用户名</li>
     *   <li>根据用户名加载用户详情（含权限信息）</li>
     *   <li>检查用户是否被启用</li>
     *   <li>创建认证令牌并存入 SecurityContext</li>
     *   <li>放行请求，继续执行后续过滤器或 Controller</li>
     * </ol>
     * <p>
     * 设计原则：
     * <ul>
     *   <li>认证失败（Token 无效）不阻断请求，只不设置用户信息</li>
     *   <li>让后续的 @PreAuthorize 或拦截器来决定是否需要登录</li>
     *   <li>这样可以支持"有些接口允许未登录访问"的场景</li>
     * </ul>
     *
     * @param request      HTTP 请求
     * @param response     HTTP 响应
     * @param filterChain  过滤器链，用于将请求传递给下一个过滤器
     * @throws ServletException  Servlet 异常
     * @throws IOException        I/O 异常
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        //  第1步：从请求头中提取 Token 
        String token = resolveToken(request);

        //  第2步：验证 Token 是否有效 
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                //  第3步：从 Token 中提取用户名 
                String username = jwtTokenProvider.getUsernameFromToken(token);

                //  第4步：根据用户名加载用户信息 
                // 会触发 CustomUserDetailsService.loadUserByUsername()
                // 从数据库查询用户信息，并封装为 UserDetails 对象
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                //  第5步：检查账号是否启用 
                // 如果账号被禁用（status = 0），不进行认证
                if (!userDetails.isEnabled()) {
                    // 继续执行后续过滤器，但不设置认证信息
                    // 相当于"Token 有效但账号已被禁用"，让后续逻辑处理
                    filterChain.doFilter(request, response);
                    return;
                }

                //  第6步：创建认证令牌 
                // UsernamePasswordAuthenticationToken 是 Spring Security 的认证令牌
                // 参数说明：
                //   - userDetails：用户信息（包含用户名、密码、权限列表）
                //   - null：凭证（密码），已由 UserDetails 携带，此处不需要重复传入
                //   - userDetails.getAuthorities()：用户权限列表（用于 @PreAuthorize）
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,                        // 用户主体
                                null,                               // 凭证（密码）
                                userDetails.getAuthorities()        // 权限列表
                        );

                //  第7步：设置请求详情 
                // 将请求的 IP、Session ID 等信息绑定到认证令牌上
                // 用于后续审计日志等场景
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                //  第8步：将认证信息存入 SecurityContext 
                // 这是最关键的一步！
                // 之后可以通过 SecurityContextHolder.getContext().getAuthentication()
                // 或 SecurityUtils.getCurrentUser() 获取当前登录用户
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (UsernameNotFoundException ignored) {
                // Token 有效，但用户已被删除（数据不一致）
                // 不写入认证信息，相当于"未登录"
                // 忽略异常，让请求继续执行
            }
        }

        //  第9步：放行请求 
        // 无论认证成功还是失败，都继续执行后续的过滤器链
        // 后续的 @PreAuthorize 会决定是否允许访问
        filterChain.doFilter(request, response);
    }

    //  私有辅助方法 

    /**
     * 从 HTTP 请求头中解析 JWT 令牌
     * <p>
     * 标准 JWT 传输方式：
     * <pre>
     * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     * </pre>
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>获取 Authorization 请求头</li>
     *   <li>检查是否以 "Bearer " 开头（注意有空格）</li>
     *   <li>去掉 "Bearer " 前缀，提取纯 Token</li>
     * </ol>
     *
     * @param request HTTP 请求
     * @return JWT 令牌字符串，如果不存在或格式不正确则返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        // 1. 获取 Authorization 请求头
        String bearer = request.getHeader("Authorization");

        // 2. 检查是否以 "Bearer " 开头
        //    StringUtils.hasText() 判断非 null 且非空字符串
        //    bearer.startsWith("Bearer ") 判断前缀
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            // 3. 从第7个字符开始截取（去掉 "Bearer " 共7个字符）
            return bearer.substring(7);
        }

        // 请求头不存在或格式错误，返回 null
        return null;
    }
}