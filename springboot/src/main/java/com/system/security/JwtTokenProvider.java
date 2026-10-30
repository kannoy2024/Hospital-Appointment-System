package com.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT（JSON Web Token）令牌生成与校验工具类
 * <p>
 * jwt:
 *   secret: ${JWT_SECRET:my-secret-key-for-jwt-256-bits}
 *   expiration: 86400000  # 毫秒，24小时
 * </pre>
 */
@Component
public class JwtTokenProvider {

    /**
     * 用于签名的密钥（SecretKey 对象）
    设置一个通用的盐来加密
     * 安全要求：密钥长度至少 256 位（32 个字符），否则会抛异常。
     */
    private final SecretKey secretKey;

    /**
     * 令牌过期时间（毫秒）
     * <p>
     * 从配置文件中读取，默认 24 小时（86400000 毫秒）。
     * 过期后令牌失效，用户需要重新登录。
     */
    private final long expiration;

    /**
     * 构造函数：从配置文件注入密钥和过期时间
     * <p>
     * 使用 {@code @Value} 注解从 Spring 的配置文件中读取值：
     * <ul>
     *   <li>${jwt.secret}：JWT 签名密钥</li>
     *   <li>${jwt.expiration}：过期时间（毫秒）</li>
     * </ul>
     *
     * @param secret     字符串形式的密钥（至少 32 字符）
     * @param expiration 过期时间（毫秒）
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        // 将字符串密钥转换为 SecretKey 对象
        // getBytes(StandardCharsets.UTF_8) 将字符串转为字节数组
        // Keys.hmacShaKeyFor() 使用 HMAC-SHA256 算法生成密钥
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // ==================== JWT 签发 ====================

    /**
     * 根据用户名生成 JWT 令牌
     * <p>
     * 业务流程：
     * <ol>
     *   <li>获取当前时间作为签发时间</li>
     *   <li>计算过期时间 = 当前时间 + 配置的过期时长</li>
     *   <li>使用 JJWT 构建 JWT</li>
     *   <li>设置载荷：用户名（subject）、签发时间、过期时间</li>
     *   <li>使用密钥签名</li>
     *   <li>压缩为字符串</li>
     * </ol>
     * <p>
     *
     * @param username 登录用户名（存储在 JWT 的 subject 字段中）
     * @return JWT 令牌字符串
     */
    public String generateToken(String username) {
        // 1. 当前时间（签发时间）
        Date now = new Date();

        // 2. 过期时间 = 当前时间 + 过期时长
        Date expiry = new Date(now.getTime() + expiration);

        // 3. 构建 JWT
        return Jwts.builder()
                // 设置 subject（主体）：通常存放用户唯一标识（这里存放用户名）
                .subject(username)
                // 设置 issuedAt（签发时间）
                .issuedAt(now)
                // 设置 expiration（过期时间）
                .expiration(expiry)
                // 使用密钥签名（HMAC-SHA256）
                .signWith(secretKey)
                // 压缩成字符串
                .compact();
    }

    // ==================== JWT 解析 ====================

    /**
     * 从 JWT 令牌中提取用户名
     * <p>
     * 注意：此方法不验证令牌是否过期或签名是否有效，
     * 只做简单的载荷解析。
     *
     * @param token JWT 令牌字符串
     * @return 用户名（即 JWT 的 subject 字段）
     * @throws io.jsonwebtoken.JwtException 如果令牌格式无效或被篡改
     */
    public String getUsernameFromToken(String token) {
        // 解析令牌并获取载荷中的 subject
        return parseClaims(token).getSubject();
    }

    /**
     * 验证 JWT 令牌是否有效
     * <p>
     * 验证内容：
     * <ol>
     *   <li><b>签名验证</b>：使用密钥验证签名是否合法，防止令牌被篡改</li>
     *   <li><b>过期验证</b>：检查过期时间是否在当前时间之后</li>
     * </ol>
     * <p>
     * 注意：此方法不会抛出异常，而是捕获异常后返回 false，
     * 便于在业务代码中直接使用。
     *
     * @param token JWT 令牌字符串
     * @return true=有效，false=无效（签名错误、过期、格式错误等）
     */
    public boolean validateToken(String token) {
        try {
            // 1. 解析令牌（会验证签名）
            Claims claims = parseClaims(token);

            // 2. 检查是否过期
            // claims.getExpiration() 获取过期时间
            // after(new Date()) 判断过期时间是否在当前时间之后
            return claims.getExpiration().after(new Date());

        } catch (Exception e) {
            // 任何异常（签名错误、过期、格式错误等）都返回 false
            return false;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 解析 JWT 令牌，获取载荷（Claims）
     * @param token JWT 令牌字符串
     * @return Claims 对象（包含所有载荷数据）
     * @throws io.jsonwebtoken.JwtException 如果签名无效或令牌格式错误
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                // 设置验证密钥（用于校验签名是否合法）
                .verifyWith(secretKey)
                // 构建解析器
                .build()
                // 解析签名的 JWT（验证签名）
                .parseSignedClaims(token)
                // 获取载荷（Payload）
                .getPayload();
    }
}