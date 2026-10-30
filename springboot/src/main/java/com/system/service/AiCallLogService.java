package com.system.service;

/**
 * AI 调用日志服务：记录 AI 导诊的每次调用情况，用于排查和统计。
 */
public interface AiCallLogService {

    /** 记录一次 AI 调用日志（调用方、模型、耗时、成功与否、错误信息） */
    void logCall(Long userId,
                 Long sessionId,
                 String provider,
                 String model,
                 String endpoint,
                 boolean success,
                 long costMs,
                 String errorMessage);
}
