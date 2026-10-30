<template>
  <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadList">
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notify-bell">
        <el-button class="topbar-icon-btn" circle title="消息通知">
          <el-icon :size="18"><Bell /></el-icon>
        </el-button>
      </el-badge>
    </template>
    <div class="notify-panel">
      <div class="notify-panel__head">
        <span class="notify-panel__title">消息通知</span>
        <el-button v-if="list.length" type="primary" link @click="readAll">全部已读</el-button>
      </div>
      <div v-loading="loading" class="notify-panel__body">
        <el-empty v-if="!loading && !list.length" description="暂无消息" :image-size="64" />
        <div
          v-for="item in list"
          :key="item.id"
          class="notify-item"
          :class="{ 'notify-item--unread': !item.read }"
          @click="openItem(item)"
        >
          <div class="notify-item__title">{{ item.title }}</div>
          <div class="notify-item__type">{{ item.typeText }}</div>
          <div class="notify-item__content">{{ item.content }}</div>
          <div class="notify-item__time">{{ formatTime(item.createTime) }}</div>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getNotifications, getUnreadCount, markRead, markAllRead } from '@/api/notification'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const list = ref([])
const unreadCount = ref(0)
let timer = null

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadUnread() {
  if (!userStore.token) return
  try {
    unreadCount.value = await getUnreadCount()
  } catch {
    // ignore
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await getNotifications({ current: 1, size: 20 })
    list.value = data.records || []
    await loadUnread()
  } finally {
    loading.value = false
  }
}

async function readAll() {
  await markAllRead()
  ElMessage.success('已全部标记已读')
  await loadList()
}

async function openItem(item) {
  if (!item.read) {
    await markRead(item.id)
    item.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  if (item.bizType === 'appointment' && item.bizId) {
    const path = userStore.isDoctor ? '/admin/doctor/appointments' : '/admin/appointments'
    router.push(path)
  }
}

onMounted(() => {
  loadUnread()
  timer = window.setInterval(loadUnread, 60000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.notify-bell {
  display: inline-flex;
  vertical-align: middle;
}

.notify-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.notify-panel__title {
  font-weight: 600;
  color: #1e293b;
}

.notify-panel__body {
  max-height: 360px;
  overflow-y: auto;
}

.notify-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
}

.notify-item:last-child {
  border-bottom: none;
}

.notify-item--unread {
  background: #f0fdf9;
  border-radius: 8px;
  padding-left: 8px;
  padding-right: 8px;
}

.notify-item__title {
  font-weight: 600;
  color: #0f172a;
}

.notify-item__type {
  font-size: 12px;
  color: #00b894;
  margin: 2px 0;
}

.notify-item__content {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
}

.notify-item__time {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}
</style>
