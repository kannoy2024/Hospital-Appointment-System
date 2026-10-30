package com.system.dto_vo.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
public class DashboardStatsVO {

    private long todayCount;
    private long totalAppointments;
    private long totalDoctors;
    private long totalUsers;
    private List<ChartItemVO> appointmentTrend = new ArrayList<>();
    private List<ChartItemVO> statusDistribution = new ArrayList<>();
    private List<ChartItemVO> departmentTop = new ArrayList<>();
    private List<ChartItemVO> ratingDistribution = new ArrayList<>();


}
