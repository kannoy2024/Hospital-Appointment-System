<template>
  <div class="page-container page-container--center">
    <div class="content-card profile-page">
      <div class="card-head">
        <div>
          <h1>个人信息</h1>
          <span class="card-head__meta">{{ profileMeta }}</span>
        </div>
        <el-button @click="$router.push(`${scopeBase}/profile/password`)">修改密码</el-button>
      </div>

      <div class="profile-avatar-row">
        <ImageUploadBox
          :src="avatarSrc"
          shape="square"
          hint="支持 JPG / PNG / GIF / WEBP，不超过 2MB"
          :loading="avatarUploading"
          @upload="handleAvatarUpload"
        />
      </div>

      <el-form
        ref="formRef"
        v-loading="loading"
        :model="form"
        :rules="rules"
        label-width="88px"
        class="profile-form"
      >
        <el-form-item label="用户名">
          <el-input :model-value="userStore.userInfo?.username" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-tag :type="roleTagType(userStore.role)" effect="light" round size="small">
            {{ roleLabel(userStore.role) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" maxlength="50" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="example@mail.com" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="20" />
        </el-form-item>
        <template v-if="isDoctor">
          <el-divider content-position="left">医生档案</el-divider>
          <el-form-item label="姓名">
            <el-input :model-value="doctorProfile?.name" disabled />
          </el-form-item>
          <el-form-item label="职称">
            <el-input :model-value="doctorProfile?.title" disabled />
          </el-form-item>
          <el-form-item label="科室">
            <el-input :model-value="doctorProfile?.departmentName" disabled />
          </el-form-item>
          <el-form-item label="简介" prop="intro">
            <el-input
              v-model="form.intro"
              type="textarea"
              :rows="4"
              placeholder="填写个人简介，将展示在患者端医生详情"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
        </template>
        <el-form-item class="profile-form-actions">
          <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
          <el-button @click="loadForm">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'Profile' })

import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getScopeBasePath } from '@/utils/route'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { roleLabel, roleTagType } from '@/utils/format'
import { updateProfile, uploadAvatar } from '@/api/profile'
import { getDoctorProfile, updateDoctorIntro } from '@/api/doctorWorkspace'
import { resolveAvatarUrl } from '@/utils/avatar'
import ImageUploadBox from '@/components/ImageUploadBox.vue'

const route = useRoute()
const userStore = useUserStore()
const scopeBase = computed(() => getScopeBasePath(route.path))
const isDoctor = computed(() => userStore.isDoctor)
const profileMeta = computed(() =>
  isDoctor.value
    ? '修改账号资料与医生简介，简介将展示在患者端'
    : '修改头像、昵称、邮箱、手机号等基本信息'
)

const doctorProfile = ref(null)

const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const avatarUploading = ref(false)

const avatarSrc = computed(() =>
  resolveAvatarUrl(userStore.userInfo?.avatar || doctorProfile.value?.avatar || '')
)

const form = reactive({
  nickname: '',
  email: '',
  phone: '',
  intro: ''
})

const rules = {
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

function loadForm() {
  const user = userStore.userInfo
  if (!user) return
  form.nickname = user.nickname || ''
  form.email = user.email || ''
  form.phone = user.phone || ''
  if (isDoctor.value && doctorProfile.value) {
    form.intro = doctorProfile.value.intro || ''
  }
}

async function loadDoctorProfile() {
  if (!isDoctor.value) return
  doctorProfile.value = await getDoctorProfile()
  form.intro = doctorProfile.value.intro || ''
}

async function handleAvatarUpload({ file }) {
  avatarUploading.value = true
  try {
    const data = await uploadAvatar(file)
    userStore.setUserInfo(data)
    ElMessage.success('头像已更新')
  } finally {
    avatarUploading.value = false
  }
}

async function handleSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const data = await updateProfile({
      nickname: form.nickname,
      email: form.email,
      phone: form.phone
    })
    userStore.setUserInfo(data)
    if (isDoctor.value) {
      doctorProfile.value = await updateDoctorIntro(form.intro)
    }
    ElMessage.success('保存成功')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    if (!userStore.userInfo) {
      await userStore.fetchUser()
    }
    await loadDoctorProfile()
    loadForm()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.profile-avatar-row {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--app-border);
}

.profile-avatar-row :deep(.image-upload-box) {
  align-items: center;
}

.profile-avatar-row :deep(.image-upload-box__hint) {
  text-align: center;
}

.profile-form {
  margin-top: 8px;
}

.profile-form-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
