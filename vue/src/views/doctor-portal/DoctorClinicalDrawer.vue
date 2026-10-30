<template>
  <el-drawer v-model="visible" :title="drawerTitle" size="800px" destroy-on-close @closed="onClosed">
    <div v-loading="loading" class="clinical-drawer">
      <el-descriptions v-if="appointment" :column="2" border size="small" class="clinical-drawer__meta">
        <el-descriptions-item label="就诊人">{{ appointment.patientName }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ appointment.patientPhone }}</el-descriptions-item>
        <el-descriptions-item label="就诊时间">{{ appointment.scheduleDate }} {{ appointment.timeSlot }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small">{{ statusText(appointment.status, appointment) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">病历</el-divider>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" :disabled="!canEditConsultation">
        <el-form-item label="主诉" prop="chiefComplaint">
          <el-input v-model="form.chiefComplaint" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="现病史">
          <el-input v-model="form.presentIllness" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="诊断" prop="diagnosis">
          <el-input v-model="form.diagnosis" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="治疗方案">
          <el-input v-model="form.treatmentPlan" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="医嘱">
          <el-input v-model="form.doctorAdvice" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="canEditConsultation">
          <el-button type="primary" :loading="savingConsultation" @click="saveConsultation">保存病历</el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">处方</el-divider>
      <div v-if="prescription && prescription.items?.length" class="clinical-drawer__rx">
        <div class="clinical-drawer__table-wrap">
          <el-table :data="prescription.items" size="small" border>
            <el-table-column prop="medicineName" label="药品" min-width="100" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" min-width="90" show-overflow-tooltip />
            <el-table-column prop="dosage" label="剂量" min-width="80" show-overflow-tooltip />
            <el-table-column prop="frequency" label="频次" min-width="72" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="56" align="center" />
            <el-table-column prop="amountFen" label="小计" width="72" align="right">
              <template #default="{ row }">{{ fenToYuanLabel(row.amountFen) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="clinical-drawer__rx-total">合计：{{ fenToYuanLabel(prescription.totalAmountFen) }}（{{ prescription.statusText }}）</div>
      </div>

      <template v-else-if="canEditPrescription">
        <div class="clinical-drawer__rx-toolbar">
          <el-select
            v-model="selectedMedicineId"
            filterable
            remote
            reserve-keyword
            placeholder="搜索药品"
            :remote-method="searchMedicines"
            :loading="medicineLoading"
            style="width: 280px"
          >
            <el-option
              v-for="item in medicineOptions"
              :key="item.id"
              :label="`${item.name} ${item.spec || ''} ${fenToYuanLabel(item.priceFen)}`"
              :value="item.id"
            />
          </el-select>
          <el-button type="primary" plain @click="addRxItem">添加药品</el-button>
        </div>
        <div class="clinical-drawer__table-wrap">
          <el-table :data="rxItems" size="small" border class="clinical-drawer__rx-table">
            <el-table-column prop="medicineName" label="药品" min-width="100" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" min-width="80" show-overflow-tooltip />
            <el-table-column label="剂量" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.dosage" size="small" class="clinical-drawer__cell-input" />
              </template>
            </el-table-column>
            <el-table-column label="频次" min-width="96">
              <template #default="{ row }">
                <el-input v-model="row.frequency" size="small" class="clinical-drawer__cell-input" />
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="80" align="center">
              <template #default="{ row }">
                <el-input
                  v-model.number="row.quantity"
                  size="small"
                  type="number"
                  :min="1"
                  class="clinical-drawer__qty-input"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="72" align="left" header-align="left">
              <template #default="{ $index }">
                <el-button type="danger" link @click="rxItems.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-button
          v-if="rxItems.length"
          type="primary"
          class="clinical-drawer__rx-submit"
          :loading="savingPrescription"
          @click="submitPrescription"
        >开具处方</el-button>
      </template>

      <div class="clinical-drawer__footer">
        <el-button
          v-if="canStart"
          type="primary"
          :loading="actionLoading"
          @click="handleStart"
        >开始接诊</el-button>
        <template v-if="canComplete">
          <p v-if="hasDraftRxItems" class="clinical-drawer__footer-hint">
            请先开具处方，或删除全部药品后再完成就诊
          </p>
          <el-button
            v-else
            type="success"
            :loading="actionLoading"
            @click="handleComplete"
          >完成就诊（无需开药）</el-button>
        </template>
        <el-button
          v-if="canDispense"
          type="warning"
          :loading="actionLoading"
          @click="handleDispense"
        >标记取药</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDoctorClinical,
  getDoctorMedicines,
  startDoctorAppointment,
  saveDoctorConsultation,
  createDoctorPrescription,
  completeDoctorAppointment,
  dispenseDoctorPrescription
} from '@/api/doctorWorkspace'
import { fenToYuanLabel } from '@/utils/format'

const props = defineProps({
  modelValue: Boolean,
  appointmentId: { type: Number, default: null }
})
const emit = defineEmits(['update:modelValue', 'updated'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const actionLoading = ref(false)
const savingConsultation = ref(false)
const savingPrescription = ref(false)
const medicineLoading = ref(false)
const appointment = ref(null)
const prescription = ref(null)
const formRef = ref()
const form = reactive({
  chiefComplaint: '',
  presentIllness: '',
  diagnosis: '',
  treatmentPlan: '',
  doctorAdvice: ''
})
const rules = {
  chiefComplaint: [{ required: true, message: '请填写主诉', trigger: 'blur' }],
  diagnosis: [{ required: true, message: '请填写诊断', trigger: 'blur' }]
}

const medicineOptions = ref([])
const selectedMedicineId = ref(null)
const rxItems = ref([])

const drawerTitle = computed(() => {
  if (!appointment.value) return '诊疗'
  const viewOnly = ['completed', 'cancelled'].includes(appointment.value.status)
  return `${viewOnly ? '查看' : '诊疗'} - ${appointment.value.patientName}`
})
const canStart = computed(() => appointment.value?.status === 'checked_in')
const canEditConsultation = computed(() =>
  ['in_consultation', 'pending_rx_pay'].includes(appointment.value?.status)
)
const canEditPrescription = computed(() => appointment.value?.status === 'in_consultation')
const canComplete = computed(() => appointment.value?.status === 'in_consultation')
const canDispense = computed(() =>
  appointment.value?.status === 'pending_rx_pay' && prescription.value?.status === 'paid'
)
const hasDraftRxItems = computed(() => rxItems.value.length > 0)

function statusText(s, row) {
  if (s === 'pending_rx_pay' && row?.prescriptionStatus === 'paid') return '待取药'
  return {
    pending: '待就诊',
    confirmed: '已确认',
    checked_in: '已到院',
    in_consultation: '就诊中',
    pending_rx_pay: '待缴药费',
    completed: '已完成',
    cancelled: '已取消'
  }[s] || s
}

watch(() => [props.modelValue, props.appointmentId], ([open, id]) => {
  if (open && id) loadClinical(id)
}, { immediate: true })

async function loadClinical(id) {
  loading.value = true
  try {
    const data = await getDoctorClinical(id)
    appointment.value = data.appointment
    prescription.value = data.prescription
    const c = data.consultation || {}
    form.chiefComplaint = c.chiefComplaint || ''
    form.presentIllness = c.presentIllness || ''
    form.diagnosis = c.diagnosis || ''
    form.treatmentPlan = c.treatmentPlan || ''
    form.doctorAdvice = c.doctorAdvice || ''
    rxItems.value = []
    await searchMedicines('')
  } finally {
    loading.value = false
  }
}

async function searchMedicines(keyword) {
  medicineLoading.value = true
  try {
    medicineOptions.value = await getDoctorMedicines({ keyword: keyword || undefined })
  } finally {
    medicineLoading.value = false
  }
}

function addRxItem() {
  const med = medicineOptions.value.find((m) => m.id === selectedMedicineId.value)
  if (!med) {
    ElMessage.warning('请选择药品')
    return
  }
  if (rxItems.value.some((i) => i.medicineId === med.id)) {
    ElMessage.warning('该药品已添加')
    return
  }
  rxItems.value.push({
    medicineId: med.id,
    medicineName: med.name,
    spec: med.spec,
    dosage: med.usageDesc || '遵医嘱',
    frequency: '每日3次',
    quantity: 1
  })
  selectedMedicineId.value = null
}

async function handleStart() {
  actionLoading.value = true
  try {
    await startDoctorAppointment(props.appointmentId)
    ElMessage.success('已开始接诊')
    await loadClinical(props.appointmentId)
    emit('updated')
  } finally {
    actionLoading.value = false
  }
}

async function saveConsultation() {
  await formRef.value.validate()
  savingConsultation.value = true
  try {
    await saveDoctorConsultation(props.appointmentId, { ...form })
    ElMessage.success('病历已保存')
    await loadClinical(props.appointmentId)
    emit('updated')
  } finally {
    savingConsultation.value = false
  }
}

async function submitPrescription() {
  if (!rxItems.value.length) {
    ElMessage.warning('请添加药品')
    return
  }
  savingPrescription.value = true
  try {
    await createDoctorPrescription(props.appointmentId, {
      items: rxItems.value.map((i) => ({
        medicineId: i.medicineId,
        dosage: i.dosage,
        frequency: i.frequency,
        quantity: i.quantity
      }))
    })
    await loadClinical(props.appointmentId)
    emit('updated')
    if (appointment.value?.status === 'pending_rx_pay') {
      ElMessage.success('处方已开具，请通知患者前往小程序缴纳药费')
      visible.value = false
    } else {
      ElMessage.success('处方已开具')
    }
  } finally {
    savingPrescription.value = false
  }
}

async function handleComplete() {
  await ElMessageBox.confirm('确认完成本次就诊（无需开药）？', '完成就诊', { type: 'info' })
  actionLoading.value = true
  try {
    await completeDoctorAppointment(props.appointmentId)
    ElMessage.success('就诊已完成')
    visible.value = false
    emit('updated')
  } finally {
    actionLoading.value = false
  }
}

async function handleDispense() {
  await ElMessageBox.confirm('确认患者已取药？', '标记取药', { type: 'info' })
  actionLoading.value = true
  try {
    await dispenseDoctorPrescription(props.appointmentId)
    ElMessage.success('已标记取药，就诊完成')
    visible.value = false
    emit('updated')
  } finally {
    actionLoading.value = false
  }
}

function onClosed() {
  appointment.value = null
  prescription.value = null
  rxItems.value = []
}
</script>

<style scoped>
.clinical-drawer {
  min-width: 0;
  padding-bottom: 8px;
}
.clinical-drawer__meta { margin-bottom: 16px; }
.clinical-drawer__table-wrap {
  width: 100%;
  overflow-x: auto;
  margin-bottom: 4px;
}
.clinical-drawer__table-wrap :deep(.clinical-drawer__rx-table) {
  width: max-content;
  min-width: 100%;
}
.clinical-drawer__table-wrap :deep(.el-table .cell) {
  padding-left: 8px;
  padding-right: 8px;
  overflow: visible;
}
.clinical-drawer :deep(.clinical-drawer__cell-input) {
  width: 100%;
}
.clinical-drawer :deep(.clinical-drawer__qty-input) {
  width: 64px;
  min-width: 64px;
}
.clinical-drawer :deep(.clinical-drawer__qty-input .el-input__wrapper) {
  padding-left: 8px;
  padding-right: 8px;
}
.clinical-drawer :deep(.clinical-drawer__qty-input .el-input__inner) {
  text-align: center;
}
.clinical-drawer__rx-total { margin-top: 12px; text-align: right; font-weight: 600; color: #0f766e; }
.clinical-drawer__rx-toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.clinical-drawer__rx-submit { margin-top: 12px; }
.clinical-drawer__footer {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.clinical-drawer__footer-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: #e6a23c;
}
</style>
