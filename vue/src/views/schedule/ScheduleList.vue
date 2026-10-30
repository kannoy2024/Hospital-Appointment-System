<template>
  <div class="page-container">
    <div class="content-card">
      <!-- 顶部统计条 -->
      <div class="stats-bar">
        <div class="stats-item">
          <span class="stats-num">{{ scheduleStats.total }}</span>
          <span class="stats-label">排班总数</span>
        </div>
        <div class="stats-divider" />
        <div class="stats-item">
          <span class="stats-num stats-num-available">{{ scheduleStats.available }}</span>
          <span class="stats-label">可预约</span>
        </div>
        <div class="stats-divider" />
        <div class="stats-item">
          <span class="stats-num stats-num-partial">{{ scheduleStats.partial }}</span>
          <span class="stats-label">部分约满</span>
        </div>
        <div class="stats-divider" />
        <div class="stats-item">
          <span class="stats-num stats-num-full">{{ scheduleStats.full }}</span>
          <span class="stats-label">已约满</span>
        </div>
        <div class="stats-divider" />
        <div class="stats-item">
          <span class="stats-num stats-num-cancelled">{{ scheduleStats.cancelled }}</span>
          <span class="stats-label">停诊</span>
        </div>
        <span class="toolbar-spacer" />
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增排班</el-button>
      </div>

      <!-- 筛选工具栏 -->
      <div class="toolbar-row">
        <div class="toolbar-group">
          <el-select v-model="doctorId" placeholder="按医生筛选" clearable filterable style="width: 180px" @change="loadSchedules">
            <el-option v-for="d in doctors" :key="d.id" :label="`${d.name} · ${d.departmentName || ''}`" :value="d.id" />
          </el-select>
        </div>
        <div class="toolbar-group week-nav">
          <el-button :icon="ArrowLeft" circle @click="shiftRange(-1)" />
          <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              clearable
              @change="loadSchedules"
          />
          <el-button :icon="ArrowRight" circle @click="shiftRange(1)" />
        </div>
        <div class="toolbar-group">
          <el-button @click="goToThisWeek" :type="isThisWeek ? 'primary' : 'default'" plain>本周</el-button>
          <el-button :icon="Refresh" @click="handleReset" circle title="重置" />
        </div>
        <div class="toolbar-spacer" />
        <el-button type="danger" :loading="exporting" @click="handleExport">导出 Excel</el-button>
      </div>

      <!-- 日历网格 -->
      <div class="schedule-grid-outer" v-loading="loading">
        <div class="schedule-grid-inner" v-if="visibleDates.length">
          <table class="grid-table">
            <thead>
            <tr>
              <th class="corner-cell">医生 / 日期</th>
              <th
                  v-for="d in visibleDates"
                  :key="d"
                  class="date-header"
                  :class="{ 'is-today': d === todayStr, 'is-weekend': isWeekend(d) }"
              >
                <span class="weekday">{{ formatWeekday(d) }}</span>
                <span class="date-num">{{ formatShortDate(d) }}</span>
                <span v-if="d === todayStr" class="today-badge">今天</span>
              </th>
            </tr>
            </thead>
            <tbody>
            <template v-for="dept in departmentGroups" :key="dept.id">
              <tr class="dept-divider">
                <td :colspan="visibleDates.length + 1">
                  <span class="dept-tag">{{ dept.name }}</span>
                  <span class="dept-count">{{ dept.doctors.length }} 位医生</span>
                </td>
              </tr>
              <tr v-for="doc in dept.doctors" :key="doc.id" class="doctor-row">
                <td class="doctor-cell">
                  <span class="doctor-avatar">{{ doc.name.charAt(0) }}</span>
                  <div class="doctor-meta">
                    <span class="doctor-name">{{ doc.name }}</span>
                    <span class="doctor-title">{{ doc.title || '医生' }}</span>
                  </div>
                </td>
                <td
                    v-for="d in visibleDates"
                    :key="d"
                    class="slot-cell"
                    :class="{ 'is-weekend': isWeekend(d) }"
                >
                  <template v-for="slot in getSlots(doc.id, d)" :key="slot.id">
                    <div
                        class="slot-item"
                        :class="slotItemClass(slot)"
                        @click="openDialog(slot)"
                    >
                      <div class="slot-time">
                        <span class="slot-time-dot" />
                        {{ slot.timeSlot }}
                      </div>
                      <div class="slot-quota">
                        <span class="quota-num" :class="{ 'quota-zero': slot.bookedCount >= slot.totalQuota }">{{ slot.bookedCount }}</span>
                        / {{ slot.totalQuota }}
                      </div>
                      <el-button
                          class="slot-delete-btn"
                          size="small"
                          circle
                          :icon="Delete"
                          @click.stop="handleDelete(slot)"
                          title="删除排班"
                      />
                    </div>
                  </template>
                  <div
                      class="cell-add-btn"
                      @click="openQuickCreate(doc, d)"
                      title="新增排班"
                  >
                    <el-icon><Plus /></el-icon>
                  </div>
                </td>
              </tr>
            </template>
            </tbody>
          </table>
        </div>
        <div v-else-if="!loading" class="grid-empty-state">
          <el-empty description="暂无排班数据，请调整筛选条件或新增排班">
            <el-button type="primary" :icon="Plus" @click="openDialog()">新增排班</el-button>
          </el-empty>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="医生" prop="doctorId">
          <el-select v-model="form.doctorId" placeholder="选择医生" filterable style="width: 100%">
            <el-option v-for="d in doctors" :key="d.id" :label="`${d.name}（${d.departmentName}）`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="出诊日期" prop="scheduleDate">
          <el-date-picker v-model="form.scheduleDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时段" prop="timeSlot">
          <el-select v-model="form.timeSlot" placeholder="选择时段" filterable allow-create style="width: 100%">
            <el-option v-for="t in timeSlots" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="总号源" prop="totalQuota">
          <el-input-number v-model="form.totalQuota" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'ScheduleList' })

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowLeft, ArrowRight, Refresh, Delete } from '@element-plus/icons-vue'
import { getSchedulePage, createSchedule, updateSchedule, deleteSchedule, exportSchedules } from '@/api/schedule'
import { getDoctorList } from '@/api/doctor'

// ---------- 基础数据 ----------
const loading = ref(false)
const submitLoading = ref(false)
const exporting = ref(false)
const doctors = ref([])
const schedules = ref([])          // 当前日期范围内所有排班
const doctorId = ref(null)
const dialogVisible = ref(false)
const formRef = ref()
const editingId = ref(null)
const timeSlots = ['08:00', '09:00', '10:00', '14:00', '15:00', '16:00']

// ---------- 日期范围 ----------
const dateRange = ref(null)

// 当前是否为"本周"
const isThisWeek = computed(() => {
  if (!dateRange.value || dateRange.value.length !== 2) return false
  return dateRange.value[0] === getThisWeekRange()[0] && dateRange.value[1] === getThisWeekRange()[1]
})

// 默认显示本周，无筛选时自动回填
function ensureDateRange() {
  if (dateRange.value && dateRange.value.length === 2) return
  goToThisWeek()
}

// 生成本周一到周日
function getThisWeekRange() {
  const now = new Date()
  const day = now.getDay() || 7            // 周日=7
  const monday = new Date(now)
  monday.setDate(now.getDate() - day + 1)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  return [fmtDate(monday), fmtDate(sunday)]
}

function goToThisWeek() {
  dateRange.value = getThisWeekRange()
  loadSchedules()
}

// 前后翻页：按当前日期范围的天数偏移
function shiftRange(dir) {
  if (!dateRange.value || dateRange.value.length !== 2) return
  const days = dayDiff(dateRange.value[0], dateRange.value[1]) + 1
  const offset = dir * days
  const start = new Date(dateRange.value[0])
  start.setDate(start.getDate() + offset)
  const end = new Date(dateRange.value[1])
  end.setDate(end.getDate() + offset)
  dateRange.value = [fmtDate(start), fmtDate(end)]
  loadSchedules()
}

function dayDiff(a, b) {
  return Math.round((new Date(b) - new Date(a)) / 86400000)
}

// 用本地时间格式化 YYYY-MM-DD，避免 toISOString 的 UTC 时区偏移（UTC+8 会往回推一天）
function fmtDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 可见日期列表
const todayStr = fmtDate(new Date())
const visibleDates = computed(() => {
  if (!dateRange.value || dateRange.value.length !== 2) return []
  const dates = []
  const cur = new Date(dateRange.value[0])
  const end = new Date(dateRange.value[1])
  while (cur <= end) {
    dates.push(fmtDate(cur))
    cur.setDate(cur.getDate() + 1)
  }
  return dates
})

// ---------- 日期格式化 ----------
const WEEKDAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function formatWeekday(dateStr) {
  const d = new Date(dateStr)
  return WEEKDAY_NAMES[d.getDay()]
}

function formatShortDate(dateStr) {
  const parts = dateStr.split('-')
  return `${parseInt(parts[1])}/${parseInt(parts[2])}`
}

function isWeekend(dateStr) {
  const d = new Date(dateStr)
  return d.getDay() === 0 || d.getDay() === 6
}

// ---------- 数据加载 ----------
async function loadSchedules() {
  if (!dateRange.value || dateRange.value.length !== 2) return
  loading.value = true
  try {
    const res = await getSchedulePage({
      current: 1,
      size: 2000,                     // 一次拉取范围内全部记录
      doctorId: doctorId.value || undefined,
      scheduleDateStart: dateRange.value[0],
      scheduleDateEnd: dateRange.value[1]
    })
    schedules.value = res.records || []
  } finally {
    loading.value = false
  }
}

// ---------- 排班统计（顶部统计条） ----------
const scheduleStats = computed(() => {
  const total = schedules.value.length
  let available = 0, partial = 0, full = 0, cancelled = 0
  for (const s of schedules.value) {
    if (s.status !== 1) { cancelled++; continue }
    if (s.bookedCount >= s.totalQuota) full++
    else if (s.bookedCount > 0) partial++
    else available++
  }
  return { total, available, partial, full, cancelled }
})

// 部门分组
const departmentGroups = computed(() => {
  const deptMap = new Map()
  for (const doc of doctors.value) {
    const deptId = doc.departmentId ?? 0
    if (!deptMap.has(deptId)) {
      deptMap.set(deptId, { id: deptId, name: doc.departmentName || '其他', doctors: [] })
    }
    deptMap.get(deptId).doctors.push(doc)
  }
  return [...deptMap.values()]
})

// 排班索引：doctorId → date → [schedule, ...]
const scheduleIndex = computed(() => {
  const map = {}
  for (const s of schedules.value) {
    const docId = s.doctorId
    const date = s.scheduleDate
    if (!map[docId]) map[docId] = {}
    if (!map[docId][date]) map[docId][date] = []
    map[docId][date].push(s)
  }
  return map
})

function getSlots(docId, date) {
  return scheduleIndex.value[docId]?.[date] || []
}

// ---------- 槽位样式 ----------
function slotItemClass(slot) {
  if (slot.status !== 1) return 'slot-cancelled'
  if (slot.bookedCount >= slot.totalQuota) return 'slot-full'
  if (slot.bookedCount > 0) return 'slot-partial'
  return 'slot-available'
}

// ---------- 弹窗操作 ----------
const defaultForm = () => ({ doctorId: null, scheduleDate: '', timeSlot: '', totalQuota: 5, status: 1 })
const form = reactive(defaultForm())
const isEdit = computed(() => editingId.value !== null)
const dialogTitle = computed(() => (isEdit.value ? '编辑排班' : '新增排班'))
const rules = {
  doctorId: [{ required: true, message: '请选择医生', trigger: 'change' }],
  scheduleDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  timeSlot: [{ required: true, message: '请选择时段', trigger: 'change' }],
  totalQuota: [{ required: true, message: '请输入号源', trigger: 'blur' }]
}

function openDialog(row) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      doctorId: row.doctorId,
      scheduleDate: row.scheduleDate,
      timeSlot: row.timeSlot,
      totalQuota: row.totalQuota,
      status: row.status
    })
  } else {
    editingId.value = null
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

// 点击空白格子 → 快速创建：医生+日期已填好
function openQuickCreate(doc, date) {
  editingId.value = null
  Object.assign(form, {
    ...defaultForm(),
    doctorId: doc.id,
    scheduleDate: date
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateSchedule(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createSchedule({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadSchedules()
  } finally {
    submitLoading.value = false
  }
}

// 删除排班
async function handleDelete(slot) {
  try {
    await ElMessageBox.confirm(
        `确定要删除 ${slot.doctorName || '医生'} ${slot.scheduleDate} ${slot.timeSlot} 的排班吗？`,
        '删除确认',
        {
          type: 'warning',
          confirmButtonText: '确定删除',
          cancelButtonText: '取消'
        }
    )
    await deleteSchedule(slot.id)
    ElMessage.success('删除成功')
    await loadSchedules()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除排班失败:', error)
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

 //导出排班明细：一行 = 一个医生的一天，六个时段横向展开
 // 后端参数为 scheduleDateStart / scheduleDateEnd，不传则默认当月；导出为全量，不受"按医生筛选"影响
async function handleExport() {
  exporting.value = true
  try {
    const [start, end] = dateRange.value || []
    const blob = await exportSchedules({
      scheduleDateStart: start || undefined,
      scheduleDateEnd: end || undefined
    })
    saveBlobFile(blob, buildExportFileName())
    ElMessage.success('导出成功，已开始下载')
  } catch {
    // 失败提示（"没有可导出数据"等）已由 request.js 统一弹出
  } finally {
    exporting.value = false
  }
}

// 与后端 ExcelExporter 命名保持一致：排班明细_yyyyMMdd_HHmmss.xlsx
function buildExportFileName() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const stamp = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  return `排班明细_${stamp}.xlsx`
}

function saveBlobFile(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 延迟释放，避免个别浏览器下载还没开始就把 URL 回收掉
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// ---------- 初始化 ----------
onMounted(async () => {
  doctors.value = await getDoctorList()
  ensureDateRange()
})

function handleReset() {
  doctorId.value = null
  goToThisWeek()
}
</script>

<style scoped>
/* ---------- 设计语言 ----------
 * 医疗管理后台 · 蓝青冷静系
 * 主色：深蓝 #1d4ed8（专业/信任）
 * 辅色：青 #0d9488（可用态）
 * 强调：琥珀 #b45309（部分约满）
 * 警示：赤红 #dc2626（已约满）
 * 中性：石板灰 #64748b
 * 底色：暖灰白 #f8fafc
 */

.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.content-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
  padding: 20px;
}

/* ---------- 顶部统计条 ---------- */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-bottom: 16px;
}

.stats-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 64px;
}

.stats-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.stats-num-available { color: #0d9488; }
.stats-num-partial { color: #b45309; }
.stats-num-full { color: #dc2626; }
.stats-num-cancelled { color: #94a3b8; }

.stats-label {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.stats-divider {
  width: 1px;
  height: 32px;
  background: #e2e8f0;
}

.toolbar-spacer {
  flex: 1;
}

/* ---------- 筛选工具栏 ---------- */
.toolbar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.week-nav {
  gap: 4px;
}

/* ---------- 网格容器 ---------- */
.schedule-grid-outer {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
}
.schedule-grid-inner {
  overflow: auto;
  max-height: 70vh;
}

/* ---------- 表格 ---------- */
.grid-table {
  border-collapse: collapse;
  min-width: 100%;
  table-layout: auto;
}
.grid-table thead {
  position: sticky;
  top: 0;
  z-index: 3;
}
.grid-table th,
.grid-table td {
  border: 1px solid #edf2f7;
  vertical-align: top;
  padding: 0;
}

/* 角标 */
.corner-cell {
  background: #f8fafc;
  font-weight: 600;
  color: #334155;
  font-size: 13px;
  text-align: center;
  padding: 12px 6px;
  min-width: 120px;
  position: sticky;
  left: 0;
  z-index: 4;
}

/* 日期头 */
.date-header {
  background: #f8fafc;
  text-align: center;
  padding: 10px 4px;
  min-width: 110px;
  width: 116px;
  font-size: 13px;
  position: relative;
}
.date-header.is-weekend {
  background: #f1f5f9;
}
.date-header.is-today {
  background: #eff6ff;
}
.date-header .weekday {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}
.date-header .date-num {
  display: block;
  font-weight: 700;
  color: #334155;
  font-size: 14px;
  margin-top: 1px;
}
.date-header.is-today .date-num {
  color: #1d4ed8;
}
.today-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  background: #1d4ed8;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 10px;
  line-height: 1.4;
}

/* 科室分隔行 */
.dept-divider td {
  background: #f1f5f9;
  padding: 6px 12px;
  border-left: none;
  border-right: none;
}
.dept-tag {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 0.5px;
}
.dept-count {
  font-size: 11px;
  color: #94a3b8;
  margin-left: 8px;
  font-weight: 400;
}

/* 医生列 */
.doctor-cell {
  background: #fff;
  padding: 8px 10px;
  text-align: left;
  min-width: 120px;
  position: sticky;
  left: 0;
  z-index: 2;
  border-right: 2px solid #e2e8f0;
}
.doctor-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #e0e7ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  vertical-align: middle;
  margin-right: 8px;
}
.doctor-meta {
  display: inline-flex;
  flex-direction: column;
  vertical-align: middle;
}
.doctor-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  line-height: 1.2;
}
.doctor-title {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  line-height: 1.2;
  margin-top: 1px;
}

/* 排班格子 */
.slot-cell {
  background: #fff;
  min-height: 80px;
  padding: 6px 6px 30px 6px;
  position: relative;
  vertical-align: top;
  width: 116px;
  transition: background 0.15s;
}
.slot-cell.is-weekend {
  background: #fafbfc;
}
.slot-cell:hover {
  background: #f5f8ff;
}

/* 排班卡片 */
.slot-item {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 8px 5px 26px;  /* 左侧预留删除按钮空间，避免 hover 时文字跳动 */
  margin: 3px 0;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}
.slot-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}

.slot-time {
  display: flex;
  align-items: center;
  gap: 5px;
  font-weight: 600;
  white-space: nowrap;
}
.slot-time-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.6;
  flex-shrink: 0;
}

.slot-quota {
  font-size: 11px;
  opacity: 0.8;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.quota-num {
  font-weight: 700;
}
.quota-zero {
  color: #dc2626;
}

/* 删除按钮 —— 固定在排班卡片左侧，避免与格子右下角的添加按钮重叠 */
.slot-delete-btn {
  position: absolute;
  right: 3px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px !important;
  height: 18px !important;
  padding: 0 !important;
  font-size: 10px !important;
  opacity: 0;
  transition: opacity 0.15s;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #e2e8f0;
  color: #94a3b8;
}
.slot-item:hover .slot-delete-btn {
  opacity: 1;
}
.slot-delete-btn:hover {
  background: #fef2f2 !important;
  border-color: #fecaca !important;
  color: #dc2626 !important;
}

/* 排班状态色 —— 收敛为低饱和、可区分、不刺眼 */
.slot-available {
  background: #f0fdfa;
  color: #0f766e;
  border-color: #99f6e4;
}
.slot-partial {
  background: #fffbeb;
  color: #92400e;
  border-color: #fde68a;
}
.slot-full {
  background: #fef2f2;
  color: #b91c1c;
  border-color: #fecaca;
}
.slot-cancelled {
  background: #f8fafc;
  color: #94a3b8;
  border-color: #e2e8f0;
  text-decoration: line-through;
}

/* 新增按钮 */
.cell-add-btn {
  position: absolute;
  bottom: 4px;
  left: 6px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #cbd5e1;
  cursor: pointer;
  transition: all 0.15s;
  font-size: 12px;
}
.slot-cell:hover .cell-add-btn {
  color: #1d4ed8;
  background: #e0e7ff;
}

/* 空状态 */
.grid-empty-state {
  padding: 48px 0;
  display: flex;
  justify-content: center;
}
</style>
