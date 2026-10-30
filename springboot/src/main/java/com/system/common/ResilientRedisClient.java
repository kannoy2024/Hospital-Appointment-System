package com.system.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 带容错的 Redis 客户端装饰器
 *
 * <p>把 Redis 的容错逻辑收口在基础设施层，业务代码只调 get/set，
 * 不关心 Redis 是否可用：
 * <ul>
 *   <li>读操作失败 → 返回 null（等价于缓存未命中，调用方查库兜底）</li>
 *   <li>写操作失败 → 忽略异常但,记录 warn 日志</li>
 *   <li>对象缓存：{@link #getObject} / {@link #setObject} 一并收口 JSON 序列化，业务无需写 try-catch</li>
 *   <li>任何一处失败 → 标记全局不可用，短时间内跳过后续 Redis 调用</li>
 *   <li>带 10 秒节流的重探 → Redis 恢复后能自动自愈</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ResilientRedisClient {
    private static final Logger log = LoggerFactory.getLogger(ResilientRedisClient.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 不可用状态下，最短重探间隔（避免每个请求都去撞连接超时）
    private static final long RECHECK_INTERVAL_MS = 10_000;

    /** 全局可用状态（volatile 保证多线程可见） */
    private volatile boolean available = true;
    private volatile long lastCheckTime = 0;

    //检查当前是否可用，不可用时按照设置的间隔重探Redis
    public boolean isAvailable() {
        if(available){
            return true;
        }
        long now = System.currentTimeMillis();//取出当前时间
        //如果
        if(now-lastCheckTime > RECHECK_INTERVAL_MS){
            lastCheckTime = now;
            return refresh();
        }
        return false;
    }

    //主动探测一次Redis是否可用
    public boolean refresh() {
        try {
            Boolean ok = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Boolean>) conn ->
                    "PONG".equalsIgnoreCase(conn.ping()));
            this.available = Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            this.available = false;
        }
        return this.available;
    }

    private void markSuccess() {
        this.available = true;
    }

    /**
     * 标记 Redis 操作失败：记录 key 与异常详情（带堆栈），便于排查"缓存为何没生效"。
     * <p>时间戳与堆栈通过 debug 级别输出，warn 保留一行关键信息，避免刷屏。
     */
    private void markFailure(String op, String key, Exception e) {
        this.available = false;
        log.warn("Redis 操作失败[{}]，进入降级。key={}，原因={}", op, key, e.getMessage());
        log.debug("Redis 操作失败堆栈[{}] key={}", op, key, e);
    }
    // ==================== 字符串读写 ====================

    /** 读：失败返回 null（等价缓存未命中） */
    public String get(String key) {
        if (!isAvailable()) return null;
        try {
            String v = redisTemplate.opsForValue().get(key);
            markSuccess();               // 成功即恢复
            return v;
        } catch (Exception e) {
            markFailure("get", key, e);
            return null;
        }
    }

    /** 批量读：失败返回 null */
    public List<String> multiGet(List<String> keys) {
        if (!isAvailable()) return null;
        try {
            List<String> v = redisTemplate.opsForValue().multiGet(keys);
            markSuccess();
            return v;
        } catch (Exception e) {
            markFailure("multiGet", String.valueOf(keys), e);
            return null;
        }
    }

    /** 写（带 TTL）：失败记为警告日志后忽略 */
    public void set(String key, String value, Duration ttl) {
        if (!isAvailable()) return;
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            markSuccess();
        } catch (Exception e) {
            markFailure("set", key, e);
        }
    }

    /** 写（无 TTL）：失败记为警告日志后忽略 */
    public void set(String key, String value) {
        if (!isAvailable()) return;
        try {
            redisTemplate.opsForValue().set(key, value);
            markSuccess();
        } catch (Exception e) {
            markFailure("set", key, e);
        }
    }

    /** 删除：失败记为警告日志后忽略 */
    public void delete(String key) {
        if (!isAvailable()) return;
        try {
            redisTemplate.delete(key);
            markSuccess();
        } catch (Exception e) {
            markFailure("delete", key, e);
        }
    }

    // ==================== 对象缓存读写（JSON 序列化收口，业务无需关心） ====================

    /**
     * 读取并反序列化为对象；未命中/反序列化失败/Redis 异常统一返回 null。
     * <p>反序列化失败时会删除该脏 key，避免反复解析失败。
     *
     * @param key   缓存 key
     * @param clazz 目标类型
     * @return 反序列化后的对象，失败返回 null
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = get(key);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，已删除脏数据。key={}，type={}", key, clazz.getSimpleName());
            log.debug("缓存反序列化失败堆栈 key={}", key, e);
            delete(key);
            return null;
        }
    }

    /**
     * 读取并反序列化为泛型对象（如 List&lt;DoctorVO&gt;）；失败统一返回 null。
     *
     * @param key  缓存 key
     * @param type 目标泛型类型（如 new TypeReference&lt;List&lt;DoctorVO&gt;&gt;() {}）
     * @return 反序列化后的对象，失败返回 null
     */
    public <T> T getObject(String key, TypeReference<T> type) {
        String json = get(key);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，已删除脏数据。key={}", key);
            log.debug("缓存反序列化失败堆栈 key={}", key, e);
            delete(key);
            return null;
        }
    }

    /**
     * 序列化对象并写入缓存（带 TTL）；序列化失败只记日志，不写缓存。
     *
     * @param key   缓存 key
     * @param value 待缓存对象
     * @param ttl   过期时间
     */
    public void setObject(String key, Object value, Duration ttl) {
        if (value == null) return;
        try {
            set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("缓存序列化失败，跳过写入。key={}，type={}", key, value.getClass().getSimpleName());
            log.debug("缓存序列化失败堆栈 key={}", key, e);
        }
    }

    /** 自增：失败返回 null */
    public Long increment(String key) {
        if (!isAvailable()) return null;
        try {
            Long v = redisTemplate.opsForValue().increment(key);
            markSuccess();
            return v;
        } catch (Exception e) {
            markFailure("increment", key, e);
            return null;
        }
    }
    /** SET NX（分布式锁）：失败返回 null（区别于 false=已被占用） */
    public Boolean setIfAbsent(String key, String value, Duration ttl) {
        if (!isAvailable()) return null;
        try {
            Boolean v = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
            markSuccess();
            return v;
        } catch (Exception e) {
            markFailure("setIfAbsent", key, e);
            return null;
        }
    }

    // ==================== ZSet（延迟队列） ====================

    public void zAdd(String key, String member, double score) {
        if (!isAvailable()) return;
        try {
            redisTemplate.opsForZSet().add(key, member, score);
            markSuccess();
        } catch (Exception e) {
            markFailure("zAdd", key, e);
        }
    }

    /** 按 score 范围取 member：失败返回 null */
    public Set<String> zRangeByScore(String key, double min, double max) {
        if (!isAvailable()) return null;
        try {
            Set<String> v = redisTemplate.opsForZSet().rangeByScore(key, min, max);
            markSuccess();
            return v;
        } catch (Exception e) {
            markFailure("zRangeByScore", key, e);
            return null;
        }
    }

    public void zRemove(String key, String member) {
        if (!isAvailable()) return;
        try {
            redisTemplate.opsForZSet().remove(key, member);
            markSuccess();
        } catch (Exception e) {
            markFailure("zRemove", key, e);
        }
    }

    // ==================== Lua 脚本 ====================
    /**
     * 执行 Lua 脚本
     * <p>返回 null 表示 Redis 异常（调用方应降级）；
     * 脚本自身的返回值（如 1/0/-1）原样透传，不会被误判为失败。
     */
    public Long executeScript(DefaultRedisScript<Long> script, List<String> keys) {
        if (!isAvailable()) return null;
        try {
            Long v = redisTemplate.execute(script, keys);
            markSuccess();
            return v;
        } catch (Exception e) {
            markFailure("executeScript", String.valueOf(keys), e);
            return null;
        }
    }

    // ==================== 按模式批量删除 ====================

    /**
     * 按模式批量删除 key（用于缓存失效），使用 SCAN 迭代避免 KEYS 阻塞 Redis。
     * <p>失败时记录日志并降级（不抛异常）。
     *
     * @param pattern 匹配模式，如 "cache:doctor:list:*"
     */
    public void deleteByPattern(String pattern) {
        if (!isAvailable()) return;
        try {
            Set<String> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            markSuccess();
        } catch (Exception e) {
            markFailure("deleteByPattern", pattern, e);
        }
    }
}
