<template>
  <div class="login-page">
    <div class="login-bg" aria-hidden="true" />
    <div class="login-card">
      <div class="login-header">
        <div class="login-logo">
          <img class="login-logo__img" src="/favicon.svg" alt="" width="32" height="32" />
        </div>
        <h1 class="login-title">{{ siteName }}</h1>
        <p class="login-subtitle">{{ adminSiteName }}</p>
      </div>

      <el-form
        ref="formRef"
        class="login-form"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="captcha">
          <div class="captcha-row">
            <el-input
              v-model="form.captcha"
              placeholder="验证码"
              :prefix-icon="Key"
              maxlength="4"
            />
            <button type="button" class="captcha-box" title="点击刷新" @click="refreshCaptcha">
              {{ captchaCode }}
            </button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-demo">
        演示：<code>admin / admin</code> · <code>doctorzmh / 123</code>
      </p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock, User } from '@element-plus/icons-vue'
import { ADMIN_SITE_NAME, SITE_NAME } from '@/config/site'
import { useUserStore } from '@/store/user'
import { getRoleHomePath } from '@/utils/route'
import { getMyMenus } from '@/api/menu'
import { setupDynamicRoutes } from '@/router/dynamicRoutes'

const router = useRouter()
const userStore = useUserStore()
const siteName = SITE_NAME
const adminSiteName = ADMIN_SITE_NAME
const formRef = ref()
const loading = ref(false)
const captchaCode = ref('')

const form = reactive({
  username: '',
  password: '',
  captcha: ''
})

function refreshCaptcha() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let code = ''
  for (let i = 0; i < 4; i += 1) {
    code += chars[Math.floor(Math.random() * chars.length)]
  }
  captchaCode.value = code
  form.captcha = ''
}

function validateCaptcha(_rule, value, callback) {
  if (!value) {
    callback(new Error('请输入验证码'))
    return
  }
  if (value.toUpperCase() !== captchaCode.value) {
    callback(new Error('验证码错误'))
    return
  }
  callback()
}

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [{ validator: validateCaptcha, trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await userStore.login({
      username: form.username,
      password: form.password
    })
    if (!userStore.isBackendUser) {
      userStore.logout()
      ElMessage.warning('患者请使用微信小程序登录')
      refreshCaptcha()
      return
    }
    const menus = await getMyMenus()
    setupDynamicRoutes(router, menus)
    ElMessage.success('登录成功')
    router.push(getRoleHomePath(data.user.role))
  } catch {
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #f8fafc;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 50% at 50% -20%, rgba(37, 99, 235, 0.12), transparent),
    radial-gradient(ellipse 60% 40% at 100% 100%, rgba(37, 99, 235, 0.06), transparent);
  pointer-events: none;
}

.login-card {
  position: relative;
  width: 100%;
  max-width: 380px;
  padding: 40px 36px 32px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
}

.login-logo__img {
  display: block;
  width: 36px;
  height: 36px;
}

.login-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--app-text);
  letter-spacing: 0.02em;
}

.login-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--app-primary) inset;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-row :deep(.el-input) {
  flex: 1;
}

.captcha-box {
  flex-shrink: 0;
  width: 100px;
  height: 40px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  color: #334155;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.18em;
  cursor: pointer;
  user-select: none;
  font-family: ui-monospace, 'Courier New', monospace;
  transition: border-color 0.15s;
}

.captcha-box:hover {
  border-color: #cbd5e1;
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 500;
  border-radius: 8px;
}

.login-footer {
  margin: 20px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.login-footer a {
  color: var(--app-primary);
  text-decoration: none;
  font-weight: 500;
}

.login-footer a:hover {
  text-decoration: underline;
}

.login-demo {
  margin: 16px 0 0;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
  text-align: center;
  font-size: 11px;
  line-height: 1.8;
  color: #94a3b8;
}

.login-demo code {
  padding: 1px 6px;
  border-radius: 4px;
  background: #f8fafc;
  color: #64748b;
  font-family: ui-monospace, monospace;
}
</style>
