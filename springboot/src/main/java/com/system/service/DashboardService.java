package com.system.service;

import com.system.dto_vo.vo.DashboardStatsVO;

/**
 * 数据看板服务：管理员首页的运营指标统计。
 */
public interface DashboardService {

    /** 获取看板统计（今日预约、总预约、医生数、患者数、趋势图等） */
    DashboardStatsVO getStats();
}
