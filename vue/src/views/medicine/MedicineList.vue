<template>
  <div class="page-container">
    <div class="content-card">
      <div class="toolbar-row">
        <el-input v-model="keyword" class="search-input" placeholder="搜索药品名称" clearable @keyup.enter="handleSearch" />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <span class="toolbar-spacer" />
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增药品</el-button>
        <el-button type="danger" :loading="exporting" @click="handleExport">导出 Excel</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" :header-cell-style="tableHeaderStyle">
        <el-table-column prop="name" label="药品名称" min-width="140" />
        <el-table-column prop="spec" label="规格" min-width="120" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="priceFen" label="单价" width="90" align="right">
          <template #default="{ row }">{{ fenToYuanLabel(row.priceFen) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" align="center" />
        <el-table-column prop="usageDesc" label="用法" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="form.spec" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="单价" prop="price"><el-input-number v-model="form.price" :min="0" :precision="2" :step="0.5" /></el-form-item>
        <el-form-item label="库存" prop="stock"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="用法"><el-input v-model="form.usageDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'MedicineList' })

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getMedicinePage, createMedicine, updateMedicine, deleteMedicine, exportMedicines } from '@/api/medicine'
import { fenToYuanLabel, fenToYuanText, yuanToFen } from '@/utils/format'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const formRef = ref()
const editingId = ref(null)

const pagination = reactive({ current: 1, size: 10, total: 0 })
const tableHeaderStyle = { background: '#f8fafc', color: '#475569', fontWeight: '600' }

const defaultForm = () => ({ name: '', spec: '', unit: '盒', price: 0, stock: 0, usageDesc: '', status: 1 })
const form = reactive(defaultForm())
const isEdit = computed(() => editingId.value !== null)
const dialogTitle = computed(() => (isEdit.value ? '编辑药品' : '新增药品'))
const rules = {
  name: [{ required: true, message: '请输入药品名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
}
const exporting = ref(false)

async function loadData() {
  loading.value = true
  try {
    const data = await getMedicinePage({ current: pagination.current, size: pagination.size, keyword: keyword.value || undefined })
    tableData.value = data.records
    pagination.total = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.current = 1; loadData() }
function handleReset() { keyword.value = ''; pagination.current = 1; loadData() }

function openDialog(row) {
  editingId.value = row?.id ?? null
  if (row) {
    // 后端金额单位是「分」，表单里按「元」编辑，只在提交时换算
    Object.assign(form, {
      name: row.name,
      spec: row.spec,
      unit: row.unit,
      price: Number(fenToYuanText(row.priceFen)),
      stock: row.stock,
      usageDesc: row.usageDesc,
      status: row.status
    })
  } else {
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = {
      name: form.name,
      spec: form.spec,
      unit: form.unit,
      priceFen: yuanToFen(form.price),
      stock: form.stock,
      usageDesc: form.usageDesc,
      status: form.status
    }
    if (isEdit.value) {
      await updateMedicine(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createMedicine(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除药品', { type: 'warning' })
  await deleteMedicine(row.id)
  ElMessage.success('已删除')
  loadData()
}


// 导出 Excel：后端写的是二进制流，request 拦截器已把它还原成 Blob
async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportMedicines({
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

// 与后端 ExcelExporter 命名保持一致：药品明细_yyyyMMdd_HHmmss.xlsx
function buildExportFileName() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const stamp = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  return `药品明细_${stamp}.xlsx`
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
