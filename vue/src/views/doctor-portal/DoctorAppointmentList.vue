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
          <el-option label="已完成" value="completed" />
          <el-option label="已取消" value="cancelled" />
        </el-select>
        <el-date-picker
          v-model="scheduleDate"
          type="date"
          placeholder="就诊日期"
          value-format="YYYY-MM-DD"
          clearable
          @change="handleSearch"
        />
      </div>

      <el-table v-loading="loading" :data="tableData" :header-cell-style="tableHeaderStyle">
        <el-table-column prop="patientName" label="就诊人" width="100" />
        <el-table-column prop="patientPhone" label="手机" width="130" />
        <el-table-column label="就诊时间" min-width="160">
          <template #default="{ row }">{{ row.scheduleDate }} {{ row.timeSlot }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="left" header-align="left">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ rowStatusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="left" header-align="left" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canClinical(row)"
              type="primary"
              link
              @click="openClinical(row)"
            >诊疗</el-button>
            <el-button
              v-else-if="canView(row)"
              type="primary"
              link
              @click="openClinical(row)"
            >查看</el-button>
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

    <DoctorClinicalDrawer
      v-model="clinicalVisible"
      :appointment-id="clinicalId"
      @updated="loadData"
    />
  </div>
</template>

<script setup>
defineOptions({ name: 'DoctorAppointmentList' })

import { ref, reactive, onMounted } from 'vue'
import { getDoctorAppointments } from '@/api/doctorWorkspace'
import DoctorClinicalDrawer from './DoctorClinicalDrawer.vue'

const loading = ref(false)
const tableData = ref([])
const status = ref('')
const scheduleDate = ref('')
const pagination = reactive({ current: 1, size: 10, total: 0 })
const tableHeaderStyle = { background: '#f8fafc', color: '#475569', fontWeight: '600' }

const clinicalVisible = ref(false)
const clinicalId = ref(null)

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
    completed: '已完成',
    cancelled: '已取消'
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

function canClinical(row) {
  return ['checked_in', 'in_consultation', 'pending_rx_pay'].includes(row.status)
}

function canView(row) {
  return row.status === 'completed'
}

async function loadData() {
  loading.value = true
  try {
    const data = await getDoctorAppointments({
      current: pagination.current,
      size: pagination.size,
      status: status.value || undefined,
      scheduleDate: scheduleDate.value || undefined
    })
    tableData.value = data.records
    pagination.total = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadData()
}

function openClinical(row) {
  clinicalId.value = row.id
  clinicalVisible.value = true
}

onMounted(loadData)
</script>
