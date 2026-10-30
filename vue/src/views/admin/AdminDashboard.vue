<template>
  <div v-loading="loading" class="page-container admin-dashboard">
    <el-row :gutter="16" class="kpi-row">
      <el-col v-for="card in kpiCards" :key="card.key" :xs="12" :sm="8" :lg="6">
        <div class="content-card kpi-card" :class="`kpi-card--${card.key}`">
          <div class="kpi-card__icon">
            <el-icon :size="22"><component :is="card.icon" /></el-icon>
          </div>
          <div class="kpi-card__body">
            <div class="kpi-card__label">{{ card.label }}</div>
            <div class="kpi-card__value">{{ card.value }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <div class="content-card chart-card">
          <h3 class="chart-card__title">近7日预约趋势</h3>
          <div ref="trendRef" class="chart-box" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="12">
        <div class="content-card chart-card">
          <h3 class="chart-card__title">预约状态分布</h3>
          <div ref="statusRef" class="chart-box" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="12">
        <div class="content-card chart-card">
          <h3 class="chart-card__title">科室预约量 TOP</h3>
          <div ref="deptRef" class="chart-box" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="12">
        <div class="content-card chart-card">
          <h3 class="chart-card__title">医生评价星级分布</h3>
          <div ref="ratingRef" class="chart-box" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
defineOptions({ name: 'AdminDashboard' })

import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Calendar, List, User, UserFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getDashboardStats } from '@/api/dashboard'

const KPI_META = [
  { key: 'todayCount', label: '今日预约', icon: Calendar },
  { key: 'totalAppointments', label: '累计预约', icon: List },
  { key: 'totalDoctors', label: '在职医生', icon: UserFilled },
  { key: 'totalUsers', label: '注册患者', icon: User }
]

const loading = ref(false)
const stats = ref({
  todayCount: 0,
  totalAppointments: 0,
  totalDoctors: 0,
  totalUsers: 0,
  appointmentTrend: [],
  statusDistribution: [],
  departmentTop: [],
  ratingDistribution: []
})

const trendRef = ref(null)
const statusRef = ref(null)
const deptRef = ref(null)
const ratingRef = ref(null)

const chartInstances = []
let resizeHandler = null

const kpiCards = computed(() => {
  const s = stats.value
  return KPI_META.map((meta) => ({
    ...meta,
    value: s[meta.key] ?? 0
  }))
})

function initChart(el, option) {
  if (!el) return null
  const chart = echarts.init(el)
  chart.setOption(option)
  chartInstances.push(chart)
  return chart
}

function buildCharts() {
  chartInstances.forEach((c) => c.dispose())
  chartInstances.length = 0

  const trend = stats.value.appointmentTrend || []
  initChart(trendRef.value, {
    color: ['#5470c6'],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 28, bottom: 32 },
    xAxis: {
      type: 'category',
      data: trend.map((i) => i.name),
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { type: 'dashed' } }
    },
    series: [{
      name: '预约量',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      data: trend.map((i) => i.value),
      areaStyle: { color: 'rgba(84, 112, 198, 0.18)' }
    }]
  })

  const status = stats.value.statusDistribution || []
  initChart(statusRef.value, {
    color: ['#5470c6', '#91cc75', '#fac858', '#ee6666'],
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      name: '预约状态',
      type: 'pie',
      radius: ['42%', '66%'],
      center: ['50%', '46%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%' },
      data: status.map((i) => ({ name: i.name, value: i.value }))
    }]
  })

  const dept = stats.value.departmentTop || []
  initChart(deptRef.value, {
    color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const p = params[0]
        const full = dept[p.dataIndex]?.name
        return `${full}<br/>${p.marker}${p.seriesName}: ${p.value}`
      }
    },
    grid: { left: 12, right: 24, top: 16, bottom: 16, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: dept.map((i) => i.name),
      inverse: true,
      axisLabel: { width: 88, overflow: 'truncate' }
    },
    series: [{
      name: '预约量',
      type: 'bar',
      data: dept.map((i) => i.value),
      barMaxWidth: 28,
      itemStyle: {
        color: (params) => ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272'][params.dataIndex % 6],
        borderRadius: [0, 4, 4, 0]
      }
    }]
  })

  const rating = stats.value.ratingDistribution || []
  initChart(ratingRef.value, {
    color: ['#fac858'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 48, right: 16, top: 24, bottom: 32 },
    xAxis: {
      type: 'category',
      data: rating.map((i) => i.name)
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { type: 'dashed' } } },
    series: [{
      name: '评价数',
      type: 'bar',
      data: rating.map((i) => i.value),
      barMaxWidth: 48,
      itemStyle: { borderRadius: [4, 4, 0, 0] }
    }]
  })
}

async function loadData() {
  loading.value = true
  try {
    stats.value = await getDashboardStats()
    await nextTick()
    buildCharts()
    chartInstances.forEach((c) => c.resize())
  } catch {
    stats.value = {
      todayCount: 0,
      totalAppointments: 0,
      totalDoctors: 0,
      totalUsers: 0,
      appointmentTrend: [],
      statusDistribution: [],
      departmentTop: [],
      ratingDistribution: []
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadData()
  resizeHandler = () => chartInstances.forEach((c) => c.resize())
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  chartInstances.forEach((c) => c.dispose())
})
</script>

<style scoped>
.admin-dashboard {
  overflow-x: hidden;
}

.kpi-row .el-col,
.chart-row .el-col {
  margin-bottom: 16px;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  min-height: 92px;
  transition: box-shadow 0.2s, transform 0.2s;
}

.kpi-card:hover {
  box-shadow: var(--app-shadow-lg, 0 8px 24px rgba(15, 23, 42, 0.08));
  transform: translateY(-1px);
}

.kpi-card__icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.kpi-card--todayCount .kpi-card__icon { background: #fff7ed; color: #ea580c; }
.kpi-card--totalAppointments .kpi-card__icon { background: #f0fdf4; color: #16a34a; }
.kpi-card--totalDoctors .kpi-card__icon { background: #f5f3ff; color: #7c3aed; }
.kpi-card--totalUsers .kpi-card__icon { background: #eff6ff; color: #2563eb; }

.kpi-card__body {
  min-width: 0;
}

.kpi-card__label {
  font-size: 13px;
  color: var(--app-text-secondary);
  margin-bottom: 6px;
}

.kpi-card__value {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  word-break: break-all;
}

.chart-card {
  padding: 16px 16px 12px;
}

.chart-card__title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--app-text);
}

.chart-box {
  position: relative;
  width: 100%;
  height: 300px;
}
</style>
