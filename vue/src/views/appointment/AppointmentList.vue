<template>
  <div class="page-container">
    <div class="content-card">
      <div class="toolbar-row">
        <el-select v-model="status" placeholder="状态" clearable style="width: 160px" @change="handleSearch">
          <el-option label="待就诊" value="pending" />
          <el-option label="已到院" value="checked_in" />
          <el-option label="就诊中" value="in_consultation" />
          <el-option label="待缴药费" value="pending_rx_pay" />
          <el-option label="待取药" value="pending_dispense" />
          <el-option label="已取消" value="cancelled" />
          <el-option label="已完成" value="completed" />
        </el-select>
        <el-input v-model="keyword" class="search-input" placeholder="就诊人/手机号" clearable @keyup.enter="handleSearch" />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <div class="toolbar-spacer" />
        <el-button type="danger" :loading="exporting" @click="handleExport">导出 Excel</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" :header-cell-style="tableHeaderStyle">
        <el-table-column prop="patientName" label="就诊人" width="100" />
        <el-table-column prop="patientPhone" label="手机" width="130" />
        <el-table-column prop="departmentName" label="科室" width="100" />
        <el-table-column prop="doctorName" label="医生" width="100" />
        <el-table-column label="就诊时间" min-width="160">
          <template #default="{ row }">{{ row.scheduleDate }} {{ row.timeSlot }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ rowStatusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="left" header-align="left">
          <template #default="{ row }">
            <div class="action-buttons">
            <el-button
              v-if="row.prescriptionId && row.prescriptionStatus === 'paid'"
              type="primary"
              link
              @click="handleDispense(row)"
            >标记取药</el-button>
            <el-button
              v-if="['pending', 'confirmed', 'in_consultation'].includes(row.status)"
              type="primary"
              link
              @click="handleComplete(row)"
            >完成</el-button>
            <el-button
              v-if="['pending', 'confirmed', 'checked_in'].includes(row.status)"
              type="danger"
              link
              @click="handleCancel(row)"
            >取消</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">

        <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50]"
            background
            layout="total, sizes, prev, pager, next"
            small
            @size-change="loadData"
            @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'AppointmentList' })

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAppointmentPage, cancelAppointment, completeAppointment, exportAppointments } from '@/api/appointment'
import { dispensePrescription } from '@/api/medicine'

const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const keyword = ref('')
const status = ref('')
const pagination = reactive({ current: 1, size: 10, total: 0 })
const tableHeaderStyle = { background: '#f8fafc', color: '#475569', fontWeight: '600' }

function rowStatusText(row) {
  if (row.status === 'pending_rx_pay' && row.prescriptionStatus === 'paid') return '待取药'
  return statusText(row.status)
}

function statusText(s) {
  return {
    pending: '待就诊',
    confirmed: '已确认',
    checked_in: '已到院',
    in_consultation: '就诊中',
    pending_rx_pay: '待缴药费',
    cancelled: '已取消',
    completed: '已完成'
  }[s] || s
}
function statusType(s) {
  return {
    pending: 'warning',
    confirmed: 'success',
    checked_in: '',
    in_consultation: 'warning',
    pending_rx_pay: 'danger',
    cancelled: 'info',
    completed: ''
  }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const data = await getAppointmentPage({ current: pagination.current, size: pagination.size, status: status.value || undefined, keyword: keyword.value || undefined })
    tableData.value = data.records
    pagination.total = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.current = 1; loadData() }

async function handleComplete(row) {
  await ElMessageBox.confirm(`确认将「${row.patientName}」的预约标记为已完成？`, '完成就诊', { type: 'info' })
  await completeAppointment(row.id)
  ElMessage.success('已标记完成')
  loadData()
}

async function handleDispense(row) {
  await ElMessageBox.confirm(`确认患者「${row.patientName}」已取药？`, '标记取药', { type: 'info' })
  await dispensePrescription(row.prescriptionId)
  ElMessage.success('已标记取药')
  loadData()
}

async function handleCancel(row) {
  await ElMessageBox.confirm(`确定取消「${row.patientName}」的预约吗？`, '取消确认', { type: 'warning' })
  await cancelAppointment(row.id)
  ElMessage.success('已取消')
  loadData()
}

// 导出 Excel：后端写的是二进制流，request 拦截器已把它还原成 Blob
async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportAppointments({
      status: status.value || undefined,
      keyword: keyword.value || undefined
    })
    saveBlobFile(blob, buildExportFileName())
    ElMessage.success('导出成功，已开始下载')
  } catch {
    // 失败提示（"没有可导出数据""超出导出上限"等）已由 request.js 统一弹出
  } finally {
    exporting.value = false
  }
}

// 与后端 ExcelExporter 命名保持一致：预约明细_yyyyMMdd_HHmmss.xlsx
function buildExportFileName() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const stamp = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  return `预约明细_${stamp}.xlsx`
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

onMounted(loadData)
</script>

<style scoped>
.action-buttons {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
