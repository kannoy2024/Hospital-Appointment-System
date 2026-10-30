<template>
  <div class="forbidden-page">
    <el-result icon="warning" title="403" sub-title="抱歉，您没有权限访问该页面">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getRoleHomePath } from '@/utils/route'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  router.push(getRoleHomePath(userStore.role))
}
</script>

<style scoped>
.forbidden-page {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
