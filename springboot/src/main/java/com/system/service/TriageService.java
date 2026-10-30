package com.system.service;

import com.system.dto_vo.vo.TriageAnalyzeResponse;

/**
 * AI 智能导诊服务：根据患者症状推荐就诊科室。
 * 优先调用 DeepSeek 大模型，失败或未配置时降级为本地关键词规则匹配。
 */
public interface TriageService {

    /** 分析患者症状，返回推荐的科室列表和紧急程度提示 */
    TriageAnalyzeResponse analyze(String symptom);
}
