package com.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.config.AiProperties;
import com.system.dto_vo.vo.DepartmentVO;
import com.system.dto_vo.vo.TriageAnalyzeResponse;
import com.system.dto_vo.vo.TriageDepartmentItem;
import com.system.service.AiCallLogService;
import com.system.service.DepartmentService;
import com.system.service.TriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能导诊服务实现：优先调用大模型完成症状分析与科室推荐，失败或未配置时降级为本地关键词规则匹配。
 */
@Service
@RequiredArgsConstructor
public class TriageServiceImpl implements TriageService {
    //制定规则
    private static final String SYSTEM_PROMPT = """
            你是医院智能导诊助手。根据患者症状，从给定的科室列表中推荐最多3个科室。
            你只能推荐列表中存在的 departmentId，禁止编造科室。
            你不是医生，不能诊断疾病，只能做导诊推荐与症状解读。
            必须返回纯 JSON，禁止 markdown 代码块，格式：
            {"symptomAnalysis":"对患者症状的详细分析（2-3句）","commonSigns":["常见病征1","常见病征2"],"preliminaryConclusion":"初步结论（非诊断，1-2句）","summary":"一句概括","advice":"就医提示","urgent":false,"departments":[{"departmentId":1,"score":4,"reason":"推荐理由","symptoms":["相关症状"]}]}
            commonSigns 列出与患者症状相关的常见病征，3-6个。若症状涉及胸痛、呼吸困难、昏迷、大出血、休克等急症，urgent 设为 true。
            """;
    //紧急关键词
    private static final List<String> URGENT_KEYWORDS = List.of(
            "胸痛", "呼吸困难", "昏迷", "大出血", "休克", "抽搐", "窒息", "严重外伤"
    );

    private final DepartmentService departmentService; //部门
    private final AiProperties aiProperties; //ai属性
    private final AiCallLogService aiCallLogService; //ai通话记录
    private final ObjectMapper objectMapper; //对象mapper
    private final RestClient aiRestClient; //REST客户端



    /**
     * 分析症状并推荐科室：优先走大模型，调用失败时降级为本地关键词匹配（最多推荐 3 个科室）。
     *
     * @param symptom 患者症状描述
     * @return 导诊分析结果（含分析文案、推荐科室、是否急症）
     */
    @Override
    public TriageAnalyzeResponse analyze(String symptom) {
        String input = symptom != null ? symptom.trim() : "";

        List<DepartmentVO> activeDepartments = departmentService.listActive(null);//拿到全部的科室id和名字，组装成map方便后续
        Map<Long, DepartmentVO> deptMap = activeDepartments.stream()
                .collect(Collectors.toMap(DepartmentVO::getId, d -> d, (a, b) -> a, LinkedHashMap::new));//转换成map

        boolean urgent = isUrgent(input);//判断是不是紧急症状

        // 策略：如果配置了AI且API Key不为空，优先尝试调用AI
        if (aiProperties.isEnabled() && StringUtils.hasText(aiProperties.getApiKey())) {
            TriageAnalyzeResponse llmResult = callLlm(input, activeDepartments, deptMap, urgent);
            if (llmResult != null) {// AI调用成功且返回了有效科室
                return llmResult;
            }
        }
        // AI调用失败（返回null），自动fall through到下面的降级逻辑
        // 降级方案：用本地的关键词规则匹配
        return fallbackAnalyze(input, deptMap, urgent);
    }

    /**
     * 调用大模型接口完成导诊分析，返回 null 表示调用失败（由调用方降级）。
     *
     * @param symptom 患者症状描述
     * @param activeDepartments 可推荐的启用科室列表
     * @param deptMap 科室ID到科室的映射（用于白名单校验）
     * @param urgentHint 本地关键词预判的急症标记
     * @return 导诊分析结果，或 null
     */
    private TriageAnalyzeResponse callLlm(String symptom,
                                          List<DepartmentVO> activeDepartments,
                                          Map<Long, DepartmentVO> deptMap,
                                          boolean urgentHint) {

        long start = System.currentTimeMillis();
        String model = aiProperties.getModel();
        String endpoint = resolveChatCompletionsUrl();

        try {
            // 1. 构造请求体（把科室列表塞进deptJson）
            String deptJson = objectMapper.writeValueAsString(activeDepartments.stream()
                    .map(d -> Map.of(
                            "departmentId", d.getId(),
                            "name", d.getName(),
                            "description", d.getDescription() != null ? d.getDescription() : ""
                    ))
                    .toList());
            // 2. 组装消息（System角色负责定规矩，User角色负责提问题）
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT + "\n科室列表：" + deptJson),
                    Map.of("role", "user", "content", "患者症状：" + symptom)
            );
            // 3. 设置请求参数（重点：response_format强制要求JSON输出）
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);// 从配置文件读取，如 deepseek-chat
            body.put("messages", messages);
            body.put("response_format", Map.of("type", "json_object"));// 强制AI吐JSON

            // 4. 用RestClient发POST请求
            String response = aiRestClient.post()
                    .uri("/chat/completions")                          // ← base URL 自动拼前面
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                    .body(body)                           
                    .retrieve()
                    .body(String.class);
            // 5. 解析响应，提取content字段（AI的真正回答）
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            // 6. 把AI吐的JSON字符串，转成Java对象
            TriageAnalyzeResponse parsed = parseLlmContent(content, deptMap, urgentHint);
            if (parsed != null && !parsed.getDepartments().isEmpty()) {
                parsed.setSource("deepseek");
                aiCallLogService.logCall(0L, null, "deepseek", model, endpoint,
                        true, System.currentTimeMillis() - start, null);
                return parsed;
            }
            // 7. 记录调用日志
            aiCallLogService.logCall(0L, null, "deepseek", model, endpoint,
                    false, System.currentTimeMillis() - start, "empty or invalid departments");
        } catch (Exception e) {
            aiCallLogService.logCall(0L, null, "deepseek", model, endpoint,
                    false, System.currentTimeMillis() - start, e.getMessage());
        }
        return null;
    }

    /**
     * 解析大模型返回的 JSON 内容为分析结果，并做科室白名单校验。
     *
     * @param content 大模型返回的 JSON 字符串
     * @param deptMap 科室ID到科室的映射（用于白名单校验）
     * @param urgentHint 本地关键词预判的急症标记
     * @return 导诊分析结果，解析失败或无可推荐科室时返回 null
     */
    private TriageAnalyzeResponse parseLlmContent(String content, Map<Long, DepartmentVO> deptMap, boolean urgentHint) {
        try {
            JsonNode node = objectMapper.readTree(content);
            TriageAnalyzeResponse resp = new TriageAnalyzeResponse();
            resp.setSummary(node.path("summary").asText("已理解您的症状描述"));
            resp.setSymptomAnalysis(node.path("symptomAnalysis").asText(
                    node.path("summary").asText("已根据您的描述完成症状分析")));
            resp.setCommonSigns(parseStringList(node.path("commonSigns")));
            resp.setPreliminaryConclusion(node.path("preliminaryConclusion").asText(
                    "建议到推荐科室进一步就诊，本结果不能替代医生诊断"));
            resp.setAdvice(node.path("advice").asText("建议尽快到推荐科室就诊，本结果仅供参考"));
            resp.setUrgent(node.path("urgent").asBoolean(urgentHint));

            List<TriageDepartmentItem> items = new ArrayList<>();
            for (JsonNode dep : node.path("departments")) {
                long deptId = dep.path("departmentId").asLong();
                // 强制白名单校验，防止ai编造科室
                DepartmentVO vo = deptMap.get(deptId);
                if (vo == null) {
                    continue;
                }
                TriageDepartmentItem item = toItem(vo);
                item.setScore(clampScore(dep.path("score").asInt(3)));
                item.setReason(dep.path("reason").asText("与您的症状相关"));
                List<String> symptoms = new ArrayList<>();
                dep.path("symptoms").forEach(s -> symptoms.add(s.asText()));
                item.setSymptoms(symptoms.isEmpty() ? List.of("相关症状") : symptoms);
                items.add(item);
            }
            if (items.isEmpty()) {
                return null;
            }
            resp.setDepartments(items.stream().limit(3).toList());
            return resp;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 降级方案：完全不调用大模型，用本地关键词规则匹配症状并推荐科室。
     *
     * @param input 患者症状描述（已 trim）
     * @param deptMap 科室ID到科室的映射
     * @param urgent 本地关键词预判的急症标记
     * @return 导诊分析结果
     */
    private TriageAnalyzeResponse fallbackAnalyze(String input, Map<Long, DepartmentVO> deptMap, boolean urgent) {
        long start = System.currentTimeMillis();
        try {
            TriageAnalyzeResponse resp = new TriageAnalyzeResponse();
            resp.setSource("fallback");
            resp.setUrgent(urgent);

            if (!StringUtils.hasText(input)) {
                resp.setSummary("未输入具体症状，为您推荐综合科室");
                resp.setSymptomAnalysis("暂未获取到具体症状描述，无法进行针对性分析。");
                resp.setCommonSigns(List.of("乏力", "不适"));
                resp.setPreliminaryConclusion("建议补充症状后重新分析，或先到全科门诊咨询。");
                resp.setAdvice("请补充症状描述以获得更精准推荐；如有急症请立即就医");
                resp.setDepartments(defaultDepartments(deptMap));
                aiCallLogService.logCall(0L, null, "fallback", "rule-based", null,
                        true, System.currentTimeMillis() - start, null);
                return resp;
            }

            Map<Long, Integer> scores = new HashMap<>();
            Map<Long, List<String>> symptomMap = new HashMap<>();
            List<TriageRule> matchedRules = new ArrayList<>();

            for (TriageRule rule : RULES) {
                boolean hit = rule.keywords().stream().anyMatch(input::contains);
                if (!hit) {
                    continue;
                }
                matchedRules.add(rule);
                for (int i = 0; i < rule.departmentIds().size(); i++) {
                    Long id = rule.departmentIds().get(i);
                    scores.merge(id, 3 - i, Integer::sum);
                    symptomMap.putIfAbsent(id, rule.symptoms());
                }
            }

            List<TriageDepartmentItem> results = scores.entrySet().stream()
                    .filter(e -> deptMap.containsKey(e.getKey()))
                    .map(e -> {
                        TriageDepartmentItem item = toItem(deptMap.get(e.getKey()));
                        item.setScore(clampScore(e.getValue() + 2));
                        item.setReason("症状关键词匹配推荐");
                        item.setSymptoms(symptomMap.getOrDefault(e.getKey(), List.of("相关症状")));
                        return item;
                    })
                    .sorted(Comparator.comparing(TriageDepartmentItem::getScore).reversed())
                    .limit(3)
                    .toList();

            if (results.isEmpty()) {
                DepartmentVO general = deptMap.get(12L);
                if (general == null && !deptMap.isEmpty()) {
                    general = deptMap.values().iterator().next();
                }
                if (general != null) {
                    TriageDepartmentItem item = toItem(general);
                    item.setScore(3);
                    item.setReason("暂未匹配到专科，推荐综合诊疗");
                    item.setSymptoms(List.of("乏力", "不适"));
                    results = List.of(item);
                } else {
                    results = List.of();
                }
            }

            resp.setSummary("根据您描述的症状「" + truncate(input, 40) + "」完成智能分析");
            fillFallbackAnalysis(resp, input, matchedRules, results);
            resp.setAdvice(urgent
                    ? "您的症状可能较紧急，建议尽快前往急诊或拨打120"
                    : "建议到推荐科室进一步就诊，本结果不能替代医生诊断");
            resp.setDepartments(results);

            aiCallLogService.logCall(0L, null, "fallback", "rule-based", null,
                    true, System.currentTimeMillis() - start, null);
            return resp;
        } catch (Exception e) {
            aiCallLogService.logCall(0L, null, "fallback", "rule-based", null,
                    false, System.currentTimeMillis() - start, e.getMessage());
            TriageAnalyzeResponse resp = new TriageAnalyzeResponse();
            resp.setSource("fallback");
            resp.setSummary("分析遇到问题，为您推荐综合科室");
            resp.setSymptomAnalysis("暂时无法完成详细症状分析。");
            resp.setCommonSigns(List.of("乏力", "不适"));
            resp.setPreliminaryConclusion("建议到全科门诊进一步评估。");
            resp.setAdvice("如有急症请立即就医");
            resp.setUrgent(urgent);
            resp.setDepartments(defaultDepartments(deptMap));
            return resp;
        }
    }

    /**
     * 填充降级模式下的症状分析与初步结论文案。
     *
     * @param resp 待填充的响应对象
     * @param input 患者症状描述
     * @param matchedRules 命中的规则列表
     * @param results 已推荐的科室项
     */
    private void fillFallbackAnalysis(TriageAnalyzeResponse resp, String input,
                                      List<TriageRule> matchedRules, List<TriageDepartmentItem> results) {
        if (matchedRules.isEmpty()) {
            resp.setSymptomAnalysis("您描述的症状「" + truncate(input, 60) + "」暂未匹配到典型病征组合，建议面诊进一步评估。");
            resp.setCommonSigns(extractMentionedSigns(input));
            String deptNames = results.stream().map(TriageDepartmentItem::getName).collect(Collectors.joining("、"));
            resp.setPreliminaryConclusion(StringUtils.hasText(deptNames)
                    ? "建议优先到" + deptNames + "就诊，由医生进一步判断。"
                    : "建议到全科门诊进一步评估。");
            return;
        }

        TriageRule primary = matchedRules.getFirst();
        resp.setSymptomAnalysis(primary.analysis());
        resp.setCommonSigns(mergeCommonSigns(matchedRules, input));
        if (!results.isEmpty()) {
            String deptNames = results.stream()
                    .map(TriageDepartmentItem::getName)
                    .limit(2)
                    .collect(Collectors.joining("、"));
            resp.setPreliminaryConclusion("综合您的症状表现，建议优先到" + deptNames + "就诊，由医生进一步判断。");
        } else {
            resp.setPreliminaryConclusion(primary.conclusion());
        }
    }

    /**
     * 合并患者自述症状与命中规则中的常见病征，最多取 6 个。
     *
     * @param matchedRules 命中的规则列表
     * @param input 患者症状描述
     * @return 去重后的常见病征列表
     */
    private List<String> mergeCommonSigns(List<TriageRule> matchedRules, String input) {
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (String sign : extractMentionedSigns(input)) {
            seen.put(sign, true);
        }
        for (TriageRule rule : matchedRules) {
            for (String sign : rule.commonSigns()) {
                seen.putIfAbsent(sign, true);
            }
        }
        return seen.keySet().stream().limit(6).toList();
    }

    /**
     * 从患者自述中提取提及的症状词。
     *
     * @param input 患者症状描述
     * @return 匹配到的症状词列表
     */
    private List<String> extractMentionedSigns(String input) {
        List<String> mentioned = new ArrayList<>();
        String[] candidates = {"头痛", "头晕", "失眠", "麻木", "发热", "咳嗽", "咳痰", "有痰", "咽痛",
                "腹痛", "腹泻", "恶心", "皮疹", "瘙痒", "牙痛", "尿频", "尿痛", "胸闷", "乏力"};
        for (String c : candidates) {
            if (input.contains(c)) {
                mentioned.add(c);
            }
        }
        if (input.contains("痰") && !mentioned.contains("咳痰")) {
            mentioned.add("咳痰");
        }
        return mentioned;
    }

    /**
     * 解析 JSON 数组节点为字符串列表，空值兜底为「相关症状」。
     *
     * @param node JSON 数组节点
     * @return 字符串列表
     */
    private List<String> parseStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                String text = n.asText();
                if (StringUtils.hasText(text)) {
                    list.add(text);
                }
            });
        }
        return list.isEmpty() ? List.of("相关症状") : list;
    }

    /**
     * 兜底推荐：无匹配结果时取前几个科室作为综合推荐。
     *
     * @param deptMap 科室ID到科室的映射
     * @return 科室推荐项列表
     */
    private List<TriageDepartmentItem> defaultDepartments(Map<Long, DepartmentVO> deptMap) {
        return deptMap.values().stream()
                .limit(3)
                .map(vo -> {
                    TriageDepartmentItem item = toItem(vo);
                    item.setScore(3);
                    item.setReason("综合推荐");
                    item.setSymptoms(List.of("常见症状"));
                    return item;
                })
                .toList();
    }

    /**
     * 将科室 VO 转为导诊推荐的科室项。
     *
     * @param vo 科室 VO
     * @return 导诊科室项
     */
    private TriageDepartmentItem toItem(DepartmentVO vo) {
        TriageDepartmentItem item = new TriageDepartmentItem();
        item.setId(vo.getId());
        item.setName(vo.getName());
        item.setDescription(vo.getDescription());
        return item;
    }

    /**
     * 根据急症关键词判断症状是否属于需要挂急诊的紧急情况。
     *
     * @param input 患者症状描述
     * @return true=命中急症关键词
     */
    private boolean isUrgent(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        return URGENT_KEYWORDS.stream().anyMatch(input::contains);
    }

    /**
     * 将评分限制在 1~5 范围内。
     *
     * @param score 原始评分
     * @return 归一化后的评分
     */
    private int clampScore(int score) {
        return Math.max(1, Math.min(5, score));
    }

    /**
     * 截断过长文本并追加省略号。
     *
     * @param text 原文本
     * @param max 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }

    /**
     * 解析大模型 chat/completions 的完整请求地址，未配置时使用默认地址。
     *
     * @return 请求地址
     */
    private String resolveChatCompletionsUrl() {
        String url = aiProperties.getApiUrl();
        if (!StringUtils.hasText(url)) {
            return "https://api.deepseek.com/chat/completions";
        }
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/chat/completions")) {
            url = url + "/chat/completions";
        }
        return url;
    }

    /**
     * 本地降级规则：关键词命中后给出的推荐科室、症状、分析与结论文案。
     */
    private record TriageRule(
            List<String> keywords,
            List<Long> departmentIds,
            List<String> symptoms,
            String analysis,
            List<String> commonSigns,
            String conclusion
    ) {
    }

    //静态规则库，当api调用失败时准备的保底方案
    private static final List<TriageRule> RULES = List.of(
            new TriageRule(
                    List.of("头痛", "头晕", "失眠", "麻木"),
                    List.of(1L, 12L),
                    List.of("头痛", "头晕", "肢体麻木"),
                    "您描述的症状以神经系统相关表现为主，头痛、头晕等可见于紧张性头痛、血压波动或颈椎问题等。",
                    List.of("头痛", "头晕", "失眠", "肢体麻木", "恶心"),
                    "初步判断可能与神经系统或全身性疾病相关，建议优先到内科或全科进一步评估。"
            ),
            new TriageRule(
                    List.of("发热", "咳嗽", "感冒", "发烧", "咽痛", "有痰", "咳痰", "痰"),
                    List.of(1L, 10L),
                    List.of("咳嗽", "咳痰", "咽痛", "发热"),
                    "您描述的症状以呼吸道表现为主，咳嗽伴痰常见于上呼吸道感染、支气管炎等，若伴发热需警惕感染性疾病。",
                    List.of("咳嗽", "咳痰", "咽痛", "发热", "胸闷", "乏力"),
                    "初步判断可能与呼吸系统疾病相关，建议优先到内科或感染相关科室就诊。"
            ),
            new TriageRule(
                    List.of("腹痛", "腹泻", "恶心", "胃"),
                    List.of(1L, 2L),
                    List.of("腹痛", "腹泻", "消化不良"),
                    "您描述的症状以消化系统不适为主，腹痛、腹泻等可见于急性胃肠炎、消化不良等。",
                    List.of("腹痛", "腹泻", "恶心", "腹胀", "食欲减退"),
                    "初步判断可能与消化系统疾病相关，建议到内科或外科消化相关门诊就诊。"
            ),
            new TriageRule(
                    List.of("骨折", "关节", "腰痛", "扭伤"),
                    List.of(4L, 11L),
                    List.of("关节疼痛", "活动受限", "腰痛"),
                    "您描述的症状以运动系统不适为主，关节疼痛、活动受限常见于软组织损伤或骨关节问题。",
                    List.of("关节疼痛", "活动受限", "腰痛", "肿胀", "压痛"),
                    "初步判断可能与骨科或运动损伤相关，建议到骨科或康复科进一步检查。"
            ),
            new TriageRule(
                    List.of("视力", "眼睛", "眼干"),
                    List.of(5L),
                    List.of("视力下降", "眼干", "视物模糊"),
                    "您描述的症状以眼部不适为主，可见于视疲劳、干眼症或屈光不正等。",
                    List.of("视力下降", "眼干", "视物模糊", "眼红", "眼痛"),
                    "初步判断可能与眼科疾病相关，建议到眼科专科就诊。"
            ),
            new TriageRule(
                    List.of("皮疹", "瘙痒", "过敏"),
                    List.of(7L, 3L),
                    List.of("皮疹", "瘙痒", "过敏"),
                    "您描述的症状以皮肤表现为主，皮疹、瘙痒常见于过敏性皮炎、荨麻疹或湿疹等。",
                    List.of("皮疹", "瘙痒", "红肿", "脱屑", "过敏"),
                    "初步判断可能与皮肤或过敏性疾病相关，建议到皮肤科或儿科（儿童）就诊。"
            ),
            new TriageRule(
                    List.of("牙", "口腔"),
                    List.of(6L),
                    List.of("牙痛", "牙龈出血"),
                    "您描述的症状以口腔不适为主，牙痛、牙龈问题常见于龋齿、牙周炎等。",
                    List.of("牙痛", "牙龈出血", "口腔溃疡", "肿胀"),
                    "初步判断可能与口腔疾病相关，建议到口腔科就诊。"
            ),
            new TriageRule(
                    List.of("尿频", "尿痛"),
                    List.of(9L),
                    List.of("尿频", "尿痛", "排尿不适"),
                    "您描述的症状以泌尿系统不适为主，尿频、尿痛常见于尿路感染等。",
                    List.of("尿频", "尿痛", "排尿不适", "尿急"),
                    "初步判断可能与泌尿系统疾病相关，建议到泌尿科就诊。"
            )
    );
}
