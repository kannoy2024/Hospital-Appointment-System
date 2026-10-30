<template>
  <div class="page-container">
    <div class="content-card">
      <div class="toolbar-row">
        <el-select v-model="departmentId" placeholder="科室" clearable style="width: 160px" @change="handleSearch">
          <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-input v-model="keyword" class="search-input" placeholder="搜索医生" clearable @keyup.enter="handleSearch" />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <span class="toolbar-spacer" />
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增医生</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" :header-cell-style="tableHeaderStyle">
        <el-table-column label="头像" width="72" align="center">
          <template #default="{ row }">
            <el-avatar :size="40" :src="resolveAvatarUrl(row.avatar)">
              {{ row.name?.charAt(0) }}
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="loginUsername" label="登录账号" width="120" show-overflow-tooltip />
        <el-table-column prop="title" label="职称" width="120" />
        <el-table-column prop="gender" label="性别" width="72" align="center">
          <template #default="{ row }">{{ row.gender === 2 ? '女' : '男' }}</template>
        </el-table-column>
        <el-table-column prop="departmentName" label="科室" width="100" />
        <el-table-column prop="intro" label="简介" min-width="180" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="头像">
          <ImageUploadBox
            :src="avatarPreview"
            hint="支持 JPG / PNG / WEBP，不超过 10MB"
            :loading="avatarUploading"
            clearable
            @upload="handleAvatarUpload"
            @clear="form.avatar = ''"
          />
        </el-form-item>
        <el-form-item label="科室" prop="departmentId">
          <el-select v-model="form.departmentId" placeholder="选择科室" style="width: 100%" @change="handleDeptChange">
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
          <div v-if="isGynecologyDept" class="form-tip">妇科仅允许女医生</div>
        </el-form-item>
        <el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="职称"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.intro" type="textarea" :rows="3" /></el-form-item>
        <el-form-item v-if="!isEdit" label="">
          <p class="form-tip form-tip--info">保存后将自动创建医生登录账号：用户名 doctor+姓名拼音首字母，默认密码 123</p>
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
defineOptions({ name: 'DoctorList' })

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { getDoctorPage, createDoctor, updateDoctor, deleteDoctor } from '@/api/doctor'
import { getDepartmentList } from '@/api/department'
import { uploadFile } from '@/api/file'
import { resolveAvatarUrl } from '@/utils/avatar'
import ImageUploadBox from '@/components/ImageUploadBox.vue'
import { el } from 'element-plus/es/locales.mjs'

const loading = ref(false)
const submitLoading = ref(false)
const avatarUploading = ref(false)
const tableData = ref([])
const departments = ref([])
const keyword = ref('')
const departmentId = ref(null)
const dialogVisible = ref(false)
const formRef = ref()
const editingId = ref(null)
const pagination = reactive({ current: 1, size: 10, total: 0 })
const tableHeaderStyle = { background: '#f8fafc', color: '#475569', fontWeight: '600' }

const defaultForm = () => ({ departmentId: null, name: '', title: '', gender: 1, avatar: '', intro: '', status: 1 })
const form = reactive(defaultForm())
const isEdit = computed(() => editingId.value !== null)
const dialogTitle = computed(() => (isEdit.value ? '编辑医生' : '新增医生'))
const avatarPreview = computed(() => resolveAvatarUrl(form.avatar))
const gynecologyDeptId = computed(() => departments.value.find((d) => d.name === '妇科')?.id ?? null)
const isGynecologyDept = computed(() => form.departmentId === gynecologyDeptId.value)
const rules = {
  departmentId: [{ required: true, message: '请选择科室', trigger: 'change' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  gender: [
    { required: true, message: '请选择性别', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (isGynecologyDept.value && value !== 2) callback(new Error('妇科仅允许女医生'))
        else callback()
      },
      trigger: 'change'
    }
  ]
}

function handleDeptChange() {
  if (isGynecologyDept.value && form.gender === 1) {
    form.gender = 2
  }
}

async function loadData() {
  loading.value = true
  try {
    const data = await getDoctorPage({ current: pagination.current, size: pagination.size, departmentId: departmentId.value || undefined, keyword: keyword.value || undefined })
    tableData.value = data.records
    pagination.total = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { pagination.current = 1; loadData() }

function openDialog(row) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, { departmentId: row.departmentId, name: row.name, title: row.title, gender: row.gender ?? 1, avatar: row.avatar || '', intro: row.intro, status: row.status })
  } else {
    editingId.value = null
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

async function handleAvatarUpload({ file }) {
  avatarUploading.value = true
  try {
    const fileVo = await uploadFile(file, 'common')
    form.avatar = fileVo.path
    ElMessage.success('头像上传成功')
  } finally {
    avatarUploading.value = false
  }
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateDoctor(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      const created = await createDoctor({ ...form })
      const account = created.loginUsername || '—'
      ElMessage.success(`创建成功，登录账号：${account}，默认密码：123`)
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除医生「${row.name}」吗？`, '删除确认', { type: 'warning' })
  await deleteDoctor(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(async () => {
  departments.value = await getDepartmentList()
  loadData()
})
</script>

<style scoped>
.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #e6a23c;
  line-height: 1.4;
}

.form-tip--info {
  margin: 0;
  color: var(--app-text-secondary);
}
</style>
