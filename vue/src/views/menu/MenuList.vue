<template>
  <div class="page-container">
    <div class="content-card">
      <div class="toolbar-row">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索名称 / 路径 / 图标"
          clearable
          :prefix-icon="Search"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <span class="toolbar-spacer" />
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增菜单</el-button>
        <el-button
          type="danger"
          :icon="Delete"
          :disabled="!selectedRows.length"
          @click="handleBatchDelete"
        >
          批量删除
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="path" label="路由路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="icon" label="图标" width="72" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.icon" :content="row.icon" placement="top">
              <el-icon :size="20" class="menu-icon-cell">
                <component :is="resolveIcon(row.icon)" />
              </el-icon>
            </el-tooltip>
            <span v-else class="icon-empty">-</span>
          </template>
        </el-table-column>
        <el-table-column label="可见角色" min-width="170">
          <template #default="{ row }">
            <template v-if="row.roleCodes && row.roleCodes.length">
              <el-tag
                v-for="code in row.roleCodes"
                :key="code"
                size="small"
                effect="plain"
                class="role-tag"
              >
                {{ roleLabel(code) }}
              </el-tag>
            </template>
            <span v-else class="icon-empty">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="parentId" label="父级ID" width="80" align="center" />
        <el-table-column prop="sortOrder" label="排序" width="72" align="center" />
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button type="primary" link @click="openRoleDialog(row)">角色</el-button>
            <el-button v-auth="'ADMIN'" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      destroy-on-close
      align-center
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" placeholder="如：用户管理" />
        </el-form-item>
        <el-form-item label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="/admin/users" />
        </el-form-item>
        <el-form-item label="父菜单" prop="parentId">
          <el-select v-model="form.parentId" placeholder="顶级菜单" style="width: 100%">
            <el-option label="顶级（无父级）" :value="0" />
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="`${item.name}（${item.path}）`"
              :value="item.id"
              :disabled="item.id === editingId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <IconPicker v-model="form.icon" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="可见角色" prop="roleCodes">
          <el-select
            v-model="form.roleCodes"
            multiple
            placeholder="请选择可见角色"
            style="width: 100%"
          >
            <el-option
              v-for="item in roleOptions"
              :key="item.code"
              :label="`${item.name}（${item.code}）`"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="roleDialogVisible"
      title="分配可见角色"
      width="460px"
      align-center
      @close="resetRoleForm"
    >
      <el-form label-width="84px">
        <el-form-item label="菜单">
          <span class="role-dialog-menu">
            {{ roleEditingRow?.name }}
            <code>{{ roleEditingRow?.path }}</code>
          </span>
        </el-form-item>
        <el-form-item label="可见角色">
          <el-checkbox-group v-model="roleForm.roleCodes">
            <el-checkbox
              v-for="item in roleOptions"
              :key="item.code"
              :value="item.code"
            >
              {{ item.name }}（{{ item.code }}）
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="菜单可见性只影响侧栏显示，接口权限由后端 @PreAuthorize 控制。"
        description="把「菜单管理」等内置菜单全部改给其他角色，管理员将无法再进入该页面，只能改库恢复。"
      />
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="handleRoleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: 'MenuList' })

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Search, Refresh } from '@element-plus/icons-vue'
import IconPicker from '@/components/IconPicker.vue'
import { resolveIcon } from '@/utils/icon'
import { getRoleList } from '@/api/role'
import {
  getMenuManageList,
  createMenu,
  updateMenu,
  updateMenuRoles,
  deleteMenu,
  deleteMenus
} from '@/api/menu'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const parentMenuList = ref([])
const keyword = ref('')
const selectedRows = ref([])
const dialogVisible = ref(false)
const formRef = ref()
const editingId = ref(null)

// 角色下拉选项（来自 GET /api/roles，只请求一次）
const roleOptions = ref([])

// 「分配角色」快捷弹窗
const roleDialogVisible = ref(false)
const roleEditingRow = ref(null)
const roleSubmitting = ref(false)
const roleForm = reactive({ roleCodes: [] })

const defaultForm = () => ({
  name: '',
  path: '',
  parentId: 0,
  icon: '',
  sortOrder: 0,
  roleCodes: []
})

const form = reactive(defaultForm())

const formRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  path: [
    { required: true, message: '请输入路由路径', trigger: 'blur' },
    {
      pattern: /^\/admin\/[a-zA-Z0-9/_-]+$/,
      message: '路径须以 /admin/ 开头',
      trigger: 'blur'
    }
  ],
  sortOrder: [{ required: true, message: '请输入排序', trigger: 'change' }],
  roleCodes: [
    { type: 'array', required: true, message: '请至少选择一个可见角色', trigger: 'change' }
  ]
}

const isEdit = computed(() => editingId.value !== null)
const dialogTitle = computed(() => (isEdit.value ? '编辑菜单' : '新增菜单'))

const parentOptions = computed(() =>
  parentMenuList.value.filter((m) => m.parentId === 0 || m.parentId === null)
)

/** 角色 code → 展示名；找不到时降级显示 code */
function roleLabel(code) {
  const hit = roleOptions.value.find((item) => item.code === code)
  return hit ? hit.name : code
}

async function loadRoleOptions() {
  if (roleOptions.value.length) {
    return
  }
  roleOptions.value = (await getRoleList()) || []
}

async function loadParentMenuList() {
  parentMenuList.value = await getMenuManageList()
}

async function loadData() {
  loading.value = true
  try {
    tableData.value = await getMenuManageList({
      keyword: keyword.value || undefined
    })
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  selectedRows.value = []
  loadData()
}

function handleReset() {
  keyword.value = ''
  selectedRows.value = []
  loadData()
}

async function openDialog(row) {
  await Promise.all([loadParentMenuList(), loadRoleOptions()])
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      name: row.name,
      path: row.path,
      parentId: row.parentId ?? 0,
      icon: row.icon || '',
      sortOrder: row.sortOrder ?? 0,
      roleCodes: [...(row.roleCodes || [])]
    })
  } else {
    editingId.value = null
    Object.assign(form, defaultForm())
  }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, defaultForm())
  editingId.value = null
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = {
      name: form.name,
      path: form.path,
      parentId: form.parentId,
      icon: form.icon || null,
      sortOrder: form.sortOrder,
      roleCodes: [...form.roleCodes]
    }
    if (isEdit.value) {
      await updateMenu(editingId.value, payload)
      ElMessage.success('更新成功，刷新页面后侧栏生效')
    } else {
      await createMenu(payload)
      ElMessage.success('创建成功，刷新页面后侧栏与路由生效')
    }
    dialogVisible.value = false
    await loadParentMenuList()
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function openRoleDialog(row) {
  await loadRoleOptions()
  roleEditingRow.value = row
  roleForm.roleCodes = [...(row.roleCodes || [])]
  roleDialogVisible.value = true
}

function resetRoleForm() {
  roleForm.roleCodes = []
  roleEditingRow.value = null
}

async function handleRoleSubmit() {
  if (!roleForm.roleCodes.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  roleSubmitting.value = true
  try {
    const updated = await updateMenuRoles(roleEditingRow.value.id, roleForm.roleCodes)
    ElMessage.success('角色已更新，刷新页面后侧栏生效')
    roleDialogVisible.value = false
    // 就地更新当前行，避免整表重查
    const target = tableData.value.find((item) => item.id === updated?.id)
    if (target) {
      target.roleCodes = updated.roleCodes
    }
  } finally {
    roleSubmitting.value = false
  }
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除菜单「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteMenu(row.id)
  ElMessage.success('删除成功')
  await loadParentMenuList()
  loadData()
}

async function handleBatchDelete() {
  const rows = selectedRows.value
  if (!rows.length) {
    return
  }
  await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 条菜单吗？内置菜单无法删除。`, '批量删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteMenus(rows.map((r) => r.id))
  ElMessage.success('批量删除成功')
  selectedRows.value = []
  await loadParentMenuList()
  loadData()
}

onMounted(() => {
  loadRoleOptions()
  loadParentMenuList()
  loadData()
})
</script>

<style scoped>
.menu-icon-cell {
  color: var(--app-primary);
  vertical-align: middle;
}

.icon-empty {
  color: var(--app-text-secondary);
}

.role-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.role-dialog-menu {
  color: var(--app-text);
}

.role-dialog-menu code {
  margin-left: 6px;
  color: var(--app-text-secondary);
  font-size: 12px;
}
</style>
