package com.system.service.impl;

import com.system.service.AiCallLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 调用日志服务实现：负责记录每次 AI 接口调用的成功/失败、耗时与错误信息，便于排查与性能分析。
 */
@Service
public class AiCallLogServiceImpl implements AiCallLogService {

    //日志
    private static final Logger log = LoggerFactory.getLogger(AiCallLogServiceImpl.class);

    // 错误信息最大长度
    private static final int MAX_ERROR_LEN = 500;

    /**
     * 记录一次 AI 调用的日志，成功记 INFO、失败记 WARN。
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param provider 供应商（如 deepseek）
     * @param model 模型名称
     * @param endpoint 接口地址
     * @param success 是否成功
     * @param costMs 耗时（毫秒）
     * @param errorMessage 失败时的错误信息
     */
    @Override
    public void logCall(Long userId, Long sessionId, String provider, String model, String endpoint,
                        boolean success, long costMs, String errorMessage) {
        // 截断过长的错误信息
        String error = truncateError(errorMessage);

        if (success) {
            // 成功 → INFO 级别，记录调用信息
            log.info("AI call ok userId={} sessionId={} provider={} model={} endpoint={} costMs={}",
                    userId, sessionId, provider, model, endpoint, costMs);
        } else {
            // 失败 → WARN 级别，多记录错误信息（因为有降级方案，不报警，所以用 WARN）
            log.warn("AI call fail userId={} sessionId={} provider={} model={} endpoint={} costMs={} error={}",
                    userId, sessionId, provider, model, endpoint, costMs, error);
        }
    }

    /**
     * 截断过长的错误信息，避免超长日志刷屏。
     *
     * @param errorMessage 原始错误信息
     * @return 截断后的错误信息（无内容时返回 null）
     */
    private String truncateError(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return null;
        }
        String text = errorMessage.trim();
        return text.length() > MAX_ERROR_LEN ? text.substring(0, MAX_ERROR_LEN) : text;
    }
}