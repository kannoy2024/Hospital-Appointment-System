package com.system.service.impl;

import com.system.dto_vo.vo.ChartItemVO;
import com.system.dto_vo.vo.DashboardStatsVO;
import com.system.mapper.DashboardMapper;
import com.system.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘服务实现：汇总核心指标、趋势、分布与排行数据供后台首页展示。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /**
     * 趋势图日期格式：MM-dd（如 07-14）
     * 用于前端 X 轴标签显示
     */
    private static final DateTimeFormatter TREND_FMT = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 预约状态枚举 -> 中文标签映射
     * 数据库存储的是英文状态码（如 pending），前端需要显示中文（如"待就诊"）
     * 此映射用于将数据库的原始值转换为用户友好的中文标签
     */
    private static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry("unpaid", "待支付"),
            Map.entry("pending", "待就诊"),
            Map.entry("confirmed", "已确认"),
            Map.entry("checked_in", "已到院"),
            Map.entry("in_consultation", "就诊中"),
            Map.entry("pending_rx_pay", "待缴药费"),
            Map.entry("completed", "已完成"),
            Map.entry("cancelled", "已取消")
    );

    private final DashboardMapper dashboardMapper;


    // ==================== 核心统计方法（对外暴露） ====================

    /**
     * 获取仪表盘统计数据，内部依次查询并组装各模块看板数据。
     *
     * @return 包含所有看板数据的统计数据VO
     */
    @Override
    public DashboardStatsVO getStats() {
        DashboardStatsVO vo = new DashboardStatsVO();

        // ========== 1. 核心指标卡片（4个数字） ==========
        vo.setTodayCount(nullToZero(dashboardMapper.countTodayAppointments()));   // 今日预约数
        vo.setTotalAppointments(nullToZero(dashboardMapper.countTotalAppointments())); // 累计预约数
        vo.setTotalDoctors(nullToZero(dashboardMapper.countActiveDoctors()));      // 在职医生总数
        vo.setTotalUsers(nullToZero(dashboardMapper.countPatients()));              // 注册患者总数

        // ========== 2. 图表数据（4个） ==========
        vo.setAppointmentTrend(buildTrend());           // 近7天预约趋势（折线图）
        vo.setStatusDistribution(buildStatusDistribution()); // 预约状态分布（饼图）
        vo.setDepartmentTop(safeList(dashboardMapper.selectDepartmentTop())); // 科室预约排行（柱状图）
        vo.setRatingDistribution(buildRatingDistribution()); // 评分分布（柱状图）

        return vo;
    }

    // ==================== 私有方法：数据加工与组装 ====================

    /**
     * 构建近7天预约趋势数据，补全缺失日期为0。
     *
     * @return 含7个数据点的趋势列表
     */
    private List<ChartItemVO> buildTrend() {
        // 1. 从数据库查询原始数据：Map 包含 day（日期）和 cnt（数量）
        Map<LocalDate, Long> countByDay = new HashMap<>();
        List<Map<String, Object>> raw = dashboardMapper.selectAppointmentTrendRaw();
        if (raw != null) {
            for (Map<String, Object> row : raw) {
                Object day = row.get("day");
                Object cnt = row.get("cnt");

                // 处理日期类型转换：MyBatis-Plus 可能返回 java.sql.Date 或 LocalDate
                if (day instanceof java.sql.Date sqlDate) {
                    countByDay.put(sqlDate.toLocalDate(), toLong(cnt));
                } else if (day instanceof LocalDate localDate) {
                    countByDay.put(localDate, toLong(cnt));
                }
            }
        }

        // 2. 生成最近7天的完整日期列表，从查询结果中填充数值
        List<ChartItemVO> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {  // 从6天前到今天的顺序
            LocalDate day = today.minusDays(i);
            String label = day.format(TREND_FMT);           // "07-14" 格式
            Long count = countByDay.getOrDefault(day, 0L); // 无数据则补0
            trend.add(new ChartItemVO(label, count));
        }
        return trend;
    }

    /**
     * 构建预约状态分布数据，将状态码映射为中文标签。
     *
     * @return 各状态对应的预约数量列表
     */
    private List<ChartItemVO> buildStatusDistribution() {
        // 1. 从数据库查询原始状态分布
        List<ChartItemVO> raw = safeList(dashboardMapper.selectStatusDistribution());

        // 2. 将状态码映射为中文标签
        List<ChartItemVO> result = new ArrayList<>();
        for (ChartItemVO item : raw) {
            String originalName = item.getName();          // 如 "pending"
            String label = STATUS_LABELS.getOrDefault(originalName, originalName); // 如 "待就诊"
            result.add(new ChartItemVO(label, item.getValue()));
        }
        return result;
    }

    /**
     * 构建评分分布数据，补全缺失的1-5星为0。
     *
     * @return 1-5星各自的评价数量列表
     */
    private List<ChartItemVO> buildRatingDistribution() {
        // 1. 从数据库查询原始评分分布
        List<ChartItemVO> raw = safeList(dashboardMapper.selectRatingDistribution());

        // 2. 转为 Map 方便快速查找
        Map<String, Long> byStar = new HashMap<>();
        for (ChartItemVO item : raw) {
            byStar.put(item.getName(), item.getValue());
        }

        // 3. 补全1-5星，确保前端显示完整的5根柱子
        List<ChartItemVO> result = new ArrayList<>();
        for (int star = 1; star <= 5; star++) {
            String key = String.valueOf(star);
            Long count = byStar.getOrDefault(key, 0L);
            result.add(new ChartItemVO(key + "星", count));  // "1星", "2星", ...
        }
        return result;
    }

    // ==================== 工具方法：数据安全与类型转换 ====================

    /**
     * null 转 0，避免后续计算出错。
     *
     * @param value 可能为 null 的 Long 值
     * @return 原值（非null）或 0
     */
    private long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    /**
     * 将数据库返回的任意数值对象统一转换为 long。
     *
     * @param value 从数据库查询返回的对象
     * @return 转换后的 long 值
     */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        // 兜底：尝试解析字符串
        return Long.parseLong(value.toString());
    }

    /**
     * 将可能为 null 的列表转为空列表，避免空指针。
     *
     * @param list 可能为 null 的列表
     * @param <T> 列表元素类型
     * @return 原列表（非null）或空列表
     */
    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : List.of();
    }
}