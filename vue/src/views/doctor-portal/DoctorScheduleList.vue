<template>
  <div class="page-container">
    <div class="content-card">
      <div class="toolbar-row">
        <el-date-picker
          v-model="scheduleDate"
          type="date"
          placeholder="出诊日期"
          value-format="YYYY-MM-DD"
          clearable
          @change="handleSearch"
        />
      </div>

      <el-table v-loading="loading" :data="tableData" :header-cell-style="tableHeaderStyle">
        <el-table-column prop="scheduleDate" label="日期" width="120" />
        <el-table-column prop="timeSlot" label="时段" width="100" />
        <el-table-column prop="departmentName" label="科室" min-width="100" />
        <el-table-column label="号源" width="100" align="center">
          <template #default="{ row }">{{ row.bookedCount }} / {{ row.totalQuota }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '停诊' }}</el-tag>
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
defineOptions({ name: 'DoctorScheduleList' })

import { ref, reactive, onMounted } from 'vue'
import { getDoctorSchedules } from '@/api/doctorWorkspace'

const loading = ref(false)
const tableData = ref([])
const scheduleDate = ref('')
const pagination = reactive({ current: 1, size: 10, total: 0 })
const tableHeaderStyle = { background: '#f8fafc', color: '#475569', fontWeight: '600' }

async function loadData() {
  loading.value = true
  try {
    const data = await getDoctorSchedules({
      current: pagination.current,
      size: pagination.size,
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

onMounted(loadData)
</script>
