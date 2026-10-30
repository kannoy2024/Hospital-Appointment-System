const { buildDateStrip } = require('../utils/format')

const MORNING_SLOTS = ['08:00', '08:30', '09:00', '09:30', '10:00', '10:30', '11:00']
const AFTERNOON_SLOTS = ['14:00', '14:30', '15:00', '15:30', '16:00', '16:30']

// 用医生 id+日期做种子生成确定性的号源时段（保证同医生同日期结果稳定）
function buildSlotsForDoctor(doctorId, date) {
  const seed = doctorId + date.replace(/-/g, '').length
  return {
    morning: MORNING_SLOTS.map((time, i) => ({
      time,
      available: (seed + i) % 4 !== 0,
      scheduleId: Number(`${doctorId}${date.replace(/-/g, '')}${i}`)
    })),
    afternoon: AFTERNOON_SLOTS.map((time, i) => ({
      time,
      available: (seed + i + 1) % 5 !== 0,
      scheduleId: Number(`${doctorId}${date.replace(/-/g, '')}${i + 10}`)
    }))
  }
}

// 查询某医生某天的排班号源
function getSchedulesByDoctor(doctorId, date) {
  return buildSlotsForDoctor(doctorId, date)
}

// 生成未来 7 天日期条
function getDateStrip() {
  return buildDateStrip(7)
}

module.exports = {
  MORNING_SLOTS,
  AFTERNOON_SLOTS,
  buildSlotsForDoctor,
  getSchedulesByDoctor,
  getDateStrip
}
