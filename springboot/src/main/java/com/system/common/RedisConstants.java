package com.system.common;

public class RedisConstants {
    public static final String QUOTA_KEY_PREFIX = "schedule:quota:"; //排班缓存前缀
    public static final String NULL_MARKER = "NULL";      // 穿透防护：缓存空值标记
    public static final long LOCK_TTL_SECONDS = 3;        // 击穿防护：互斥锁超时

    public static final String  APPOINTMENT_LOCK_PREFIX ="lock:appointment:"; //预约被锁定的前缀

    public static final String  APPOINTMENT_LOCK_QUOTA = "lock:quota:"; //预约被锁定的配额

    public static final String DEPARTMENT_CACHE_PREFIX = "cache:department:list";    // 科室列表缓存前缀

    public static final String DOCTOR_DETAIL_CACHE_SUFFIX = "cache:doctor:detail:";

    public static final String DELAY_APPOINTMENT_KEY="delay:appointment"; //预约超时延迟队列的key

    public static final long APPOINTMENT_EXPIRE_MINUTES = 30;//预约超时时间

    public static final String SYSTEM_STATUS_PREFIX="system:status:";//系统状态前缀

    public static final String DOCTOR_LIST_CACHE_PREFIX = "cache:doctor:list:"; // 医生列表缓存前缀


    //SpringCache缓存常量


    // 科室列表缓存（无关键字时）
    public static final String CACHE_DEPARTMENT_LIST = "department:list";

    // 医生列表缓存（按 科室 + 是否有号 分维度）
    public static final String CACHE_DOCTOR_LIST = "doctor:list";

    // 医生详情缓存（按 id）
    public static final String CACHE_DOCTOR_DETAIL = "doctor:detail";

}
