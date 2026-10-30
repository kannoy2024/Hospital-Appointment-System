<template>
  <div class="page-container">
    <div class="content-card">
      <div class="toolbar-row">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="姓名 / 手机号 / 身份证"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" :header-cell-style="tableHeaderStyle">
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column label="手机号" width="132">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="身份证" min-width="190">
          <template #default="{ row }">{{ maskIdCard(row.idCard) }}</template>
        </el-table-column>
        <el-table-column label="性别" width="72" align="center">
          <template #default="{ row }">{{ GENDER_LABELS[row.gender] ?? '未知' }}</template>
        </el-table-column>
        <el-table-column label="出生日期" width="120">
          <template #default="{ row }">{{ row.birthDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="婚姻" width="80" align="center">
          <template #default="{ row }">{{ MARITAL_LABELS[row.marital] ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.address || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="left" header-align="left">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="就诊人详情" width="560px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证" :span="2">{{ maskIdCard(detail.idCard) }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ GENDER_LABELS[detail.gender] ?? '未知' }}</el-descriptions-item>
        <el-descriptions-item label="出生日期">{{ detail.birthDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="婚姻">{{ MARITAL_LABELS[detail.marital] ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="默认就诊人">{{ detail.isDefault === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="民族编号">{{ detail.nation ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="就诊人ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ detail.address || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'PatientList' })

import { ref, reactive, onMounted } from 'vue'
import { getPatient, getPatientPage } from '@/api/patient'

// patient.gender：1男 2女 0未知；patient.marital：1已婚 2未婚
const GENDER_LABELS = { 0: '未知', 1: '男', 2: '女' }
const MARITAL_LABELS = { 1: '已婚', 2: '未婚' }

const loading = ref(false)
const tableData = ref([])
const keyword = ref('')
const pagination = reactive({ current: 1, size: 10, total: 0 })
const tableHeaderStyle = { background: '#f8fafc', color: '#475569', fontWeight: '600' }

const detailVisible = ref(false)
const detail = ref(null)

async function loadData() {
  loading.value = true
  try {
    const data = await getPatientPage({
      current: pagination.current,
      size: pagination.size,
      keyword: keyword.value || undefined
    })
    tableData.value = data.records || []
    pagination.total = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadData()
}

// 先用手上的行数据秒开，再拉详情接口覆盖（详情比列表权威）
async function openDetail(row) {
  detail.value = { ...row }
  detailVisible.value = true
  try {
    detail.value = await getPatient(row.id)
  } catch {
    // 失败提示已由 request.js 统一弹出，弹窗保留列表里的数据
  }
}

// 身份证脱敏：保留前 4 位与后 4 位
function maskIdCard(idCard) {
  if (!idCard) return '-'
  if (idCard.length < 8) return idCard
  return `${idCard.slice(0, 4)}**********${idCard.slice(-4)}`
}

onMounted(loadData)
</script>