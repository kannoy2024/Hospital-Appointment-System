package com.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 配置类
 */
@Configuration
public class RedisConfig {

    /**
     * 创建 StringRedisTemplate 实例
     * 用于操作 Redis 中的字符串类型数据
     * 使用场景：
     * - 缓存 Token
     * - 存储验证码
     * - 号源计数
     *
     * @param factory Redis 连接工厂（Spring Boot 自动配置）
     * @return StringRedisTemplate 实例
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        // 创建模板
        StringRedisTemplate template = new StringRedisTemplate(factory);

        // 显式设置 Key 序列化器为字符串
        template.setKeySerializer(RedisSerializer.string());

        // 显式设置 Value 序列化器为字符串
        template.setValueSerializer(RedisSerializer.string());

        return template;
    }

    /**
     * 加载号源扣减 Lua 脚本
     * 作用：将 classpath 下的 lua/quota_decr.lua 文件加载为 Redis 脚本对象
     * 脚本文件位置：
     * - src/main/resources/lua/quota_decr.lua
     * 脚本示例（逻辑）：
     * -- 扣减号源脚本
     * local key = KEYS[1]      -- 号源 key
     *  -1            -- 号源已用完
     * return quota - 1         -- 返回剩余号源
     * @return Redis 脚本对象，返回类型为 Long（剩余号源数）
     */
    @Bean
    public DefaultRedisScript<Long> quotaDecrScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        // 设置脚本位置：从 classpath 加载 lua/quota_decr.lua 文件
        script.setLocation(new org.springframework.core.io.ClassPathResource("lua/quota_decr.lua"));

        // 设置脚本返回类型为 Long
        // 脚本中 return -1 或 return 剩余号源数，都会 自动转为 Long
        script.setResultType(Long.class);

        return script;
    }

}