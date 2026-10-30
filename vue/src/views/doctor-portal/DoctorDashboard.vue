<template>
  <div v-loading="loading" class="page-container doctor-dashboard">
    <el-row v-if="profile" :gutter="16" class="profile-row">
      <el-col :span="24">
        <div class="content-card profile-card">
          <div class="profile-card__main">
            <el-avatar v-if="profile.avatar" :size="56" :src="avatarUrl" class="profile-card__avatar" />
            <div>
              <h2>{{ profile.name }} <span class="profile-card__title">{{ profile.title }}</span></h2>
              <p>{{ profile.departmentName }} · {{ profile.intro }}</p>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="kpi-row">
      <el-col v-for="card in statCards" :key="card.key" :xs="12" :md="8">
        <div class="content-card kpi-card" :class="`kpi-card--${card.key}`">
          <div class="kpi-card__icon">
            <el-icon :size="22"><component :is="card.icon" /></el-icon>
          </div>
          <div class="kpi-card__body">
            <div class="kpi-card__label">{{ card.label }}</div>
            <div class="kpi-card__value">{{ card.value }}</div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
defineOptions({ name: 'DoctorDashboard' })

import { ref, computed, onMounted } from 'vue'
import { Calendar, CircleCheck, Clock } from '@element-plus/icons-vue'
import { getDoctorProfile, getDoctorStats } from '@/api/doctorWorkspace'
import { resolveAvatarUrl } from '@/utils/avatar'

const loading = ref(false)
const profile = ref(null)
const stats = ref({ todayCount: 0, pendingCount: 0, completedCount: 0 })

const avatarUrl = computed(() => resolveAvatarUrl(profile.value?.avatar))

const statCards = computed(() => [
  { key: 'today', label: '今日预约', value: stats.value.todayCount, icon: Calendar },
  { key: 'pending', label: '待就诊', value: stats.value.pendingCount, icon: Clock },
  { key: 'completed', label: '已完成', value: stats.value.completedCount, icon: CircleCheck }
])

onMounted(async () => {
  loading.value = true
  try {
    const [p, s] = await Promise.all([getDoctorProfile(), getDoctorStats()])
    profile.value = p
    stats.value = s
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.profile-row .el-col {
  margin-bottom: 16px;
}

.profile-card {
  padding: 20px 24px;
}

.profile-card__main {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-card__avatar {
  flex-shrink: 0;
}

.profile-card h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: var(--app-text);
}

.profile-card__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-secondary);
}

.profile-card p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 14px;
}

.kpi-row .el-col {
  margin-bottom: 16px;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  min-height: 92px;
}

.kpi-card__icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.kpi-card--today .kpi-card__icon { background: #fff7ed; color: #ea580c; }
.kpi-card--pending .kpi-card__icon { background: #eff6ff; color: #2563eb; }
.kpi-card--completed .kpi-card__icon { background: #f0fdf4; color: #16a34a; }

.kpi-card__label {
  font-size: 13px;
  color: var(--app-text-secondary);
  margin-bottom: 6px;
}

.kpi-card__value {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text);
}
</style>
