package com.system.config;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.system.common.RedisConstants.*;


/**
 * Spring Cache 配置：
 * 1 开启注解缓存
 * 2 自定义 RedisCacheManager（key 前缀、各缓存 TTL、JSON 序列化）
 * 3 容错：Redis 异常只记日志并降级回源 DB
 */
@Configuration
@EnableCaching

public class CacheConfig implements CachingConfigurer {

    private static final Logger log= LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory){
        // 1. 专用 ObjectMapper：写入类型信息(支持泛型 List<VO> 反序列化) + 支持 LocalDateTime
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        // 2. 基础配置：key 前缀 cache:{cacheName}: + 默认 TTL + JSON 值序列化
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .computePrefixWith(name -> "cache:" + name + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                .disableCachingNullValues();

        // 3. 各缓存单独 TTL（对齐原手写 TTL）
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(CACHE_DEPARTMENT_LIST, base.entryTtl(Duration.ofHours(2)));
        configs.put(CACHE_DOCTOR_LIST, base.entryTtl(Duration.ofMinutes(3)));
        configs.put(CACHE_DOCTOR_DETAIL, base.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(configs)
                .transactionAware()   // 与 @Transactional 配合：提交后才写/清缓存
                .build();
    }

    /**
     * 容错处理：Redis 不可用时只记日志，不抛异常，业务自动回源 DB。
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(@NonNull RuntimeException e,
                                            @NonNull Cache cache,
                                            @NonNull Object key) {
                log.warn("缓存读失败，降级回源。cache={}, key={}, 原因={}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(@NonNull RuntimeException e,
                                            @NonNull Cache cache,
                                            @NonNull Object key,
                                            Object value) {
                log.warn("缓存写失败，忽略。cache={}, key={}, 原因={}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(@NonNull RuntimeException e,
                                              @NonNull Cache cache,
                                              @NonNull Object key) {
                log.warn("缓存清除失败，忽略。cache={}, key={}, 原因={}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(@NonNull RuntimeException e,
                                              @NonNull Cache cache) {
                log.warn("缓存清空失败，忽略。cache={}, 原因={}",
                        cache.getName(), e.getMessage());
            }
        };
    }


}
