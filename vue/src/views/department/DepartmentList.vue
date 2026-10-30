<template>
  <div class="page-container">
    <div class="content-card">
      <div class="toolbar-row">
        <el-input v-model="keyword" class="search-input" placeholder="搜索科室名称" clearable @keyup.enter="handleSearch" />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <span class="toolbar-spacer" />
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增科室</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" :header-cell-style="tableHeaderStyle">
        <el-table-column prop="name" label="科室名称" min-width="120" />
        <el-table-column prop="location" label="位置" min-width="120" show-overflow-tooltip />
        <el-table-column prop="description" label="简介" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
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
        <el-form-item label="位置"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
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
defineOptions({ name: 'DepartmentList' })

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getDepartmentPage, createDepartment, updateDepartment, deleteDepartment } from '@/api/department'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const formRef = ref()
const editingId = ref(null)

const pagination = reactive({ current: 1, size: 10, total: 0 })
const tableHeaderStyle = { background: '#f8fafc', color: '#475569', fontWeight: '600' }

const defaultForm = () => ({ name: '', description: '', location: '', sortOrder: 0, status: 1 })
const form = reactive(defaultForm())
const isEdit = computed(() => editingId.value !== null)
const dialogTitle = computed(() => (isEdit.value ? '编辑科室' : '新增科室'))
const rules = { name: [{ required: true, message: '请输入科室名称', trigger: 'blur' }] }

async function loadData() {
  loading.value = true
  try {
    const data = await getDepartmentPage({ current: pagination.current, size: pagination.size, keyword: keyword.value || undefined })
    tableData.value = data.records
    pagination.total = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.current = 1; loadData() }
function handleReset() { keyword.value = ''; pagination.current = 1; loadData() }

function openDialog(row) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, { name: row.name, description: row.description, location: row.location, sortOrder: row.sortOrder, status: row.status })
  } else {
    editingId.value = null
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateDepartment(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createDepartment({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除科室「${row.name}」吗？`, '删除确认', { type: 'warning' })
  await deleteDepartment(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
