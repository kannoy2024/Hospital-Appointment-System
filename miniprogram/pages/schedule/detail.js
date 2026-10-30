const api = require('../../utils/api')
const {
  enrichDoctor,
  groupSchedules
} = require('../../utils/adapter')
const {
  buildDateStrip
} = require('../../utils/format')
const money = require('../../utils/money')
const toast = require('../../utils/toast')
const auth = require('../../utils/auth')

Page({
  data: {
    doctor: null,
    dateList: [],
    selectedDate: '',
    morning: [],
    afternoon: [],
    hasAnySchedule: false,
    hasAvailable: false,
    selectedTime: '',
    selectedScheduleId: 0,
    feeText: '',
    favorited: null, // 新增
    favBusy: false // 新增
  },

  // 页面加载时触发，校验医生参数、生成日期条并拉取医生信息与号源
  onLoad(options) {
    const doctorId = Number(options.doctorId)
    if (!doctorId) {
      wx.showToast({
        title: '参数错误',
        icon: 'none'
      })
      return
    }
    const dateList = buildDateStrip(7)
    const selectedDate = dateList[0].value
    this.setData({
      dateList,
      selectedDate,
      doctorId
    })
    api.getDoctorById(doctorId)
      .then((doctor) => {
        this.setData({
          doctor: enrichDoctor(doctor, true),
          // 挂号费与 doctor 无关（后端 DoctorVO 无费用字段），统一取 money.REGISTRATION_FEE_FEN
          feeText: money.fenToYuanLabel(money.REGISTRATION_FEE_FEN)
        })
      })
      .catch((err) => toast(err.message || '医生不存在'))
    this.loadSlots()
    this.loadFavoriteState()
  },

  // 按当前医生与选中日期加载号源，并拆分为上午/下午时段
  loadSlots() {
    const {
      doctorId,
      selectedDate
    } = this.data
    api.getSchedules({
        doctorId,
        scheduleDate: selectedDate
      })
      .then((schedules) => {
        const slots = groupSchedules(schedules)
        this.setData({
          morning: slots.morning,
          afternoon: slots.afternoon,
          hasAnySchedule: slots.hasAnySchedule,
          hasAvailable: slots.hasAvailable,
          selectedTime: '',
          selectedScheduleId: 0
        })
      })
      .catch((err) => toast(err.message || '加载号源失败'))
  },

  // 切换就诊日期并重新加载该日号源
  onDateChange(e) {
    this.setData({
      selectedDate: e.detail.value
    })
    this.loadSlots()
  },

  // 选中某个就诊时段，记录时段名称与号源 id
  onSlotSelect(e) {
    this.setData({
      selectedTime: e.detail.time,
      selectedScheduleId: e.detail.scheduleId
    })
  },

  // 校验已选时段后跳转到预约确认页
  // 新增校验是否登录，放在传递空信息给后端
  onBook() {
    const {
      doctor,
      selectedDate,
      selectedTime,
      selectedScheduleId
    } = this.data
    if (!selectedTime || !selectedScheduleId) {
      wx.showToast({
        title: '请选择就诊时段',
        icon: 'none'
      })
      return
    }
    if (!auth.ensureLogin({
        content: "预约需要登录，是否前往登录？"
      })) return
    wx.navigateTo({
      url: `/pages/appointment/confirm?doctorId=${doctor.id}&scheduleDate=${selectedDate}&timeSlot=${selectedTime}&scheduleId=${selectedScheduleId}`
    })
  },

  // 查询是否已收藏；未登录不发请求，避免必然的 401
  loadFavoriteState() {
    if (!auth.isLoggedIn()) {
      this.setData({
        favorited: false
      })
      return
    }
    api.checkFavorite(this.data.doctorId)
      .then((fav) => this.setData({
        favorited: !!fav
      }))
      .catch(() => this.setData({
        favorited: false
      }))
  },

  // 切换收藏：未登录先引导登录；请求期间加锁防连点
  onToggleFav() {
    if (this.data.favBusy) return
    if (!auth.ensureLogin({
        content: '收藏医生需要登录，是否前往登录？'
      })) return
    const doctorId = this.data.doctorId
    const next = !this.data.favorited
    this.setData({
      favBusy: true
    })
    const task = next ? api.addFavorite(doctorId) : api.removeFavorite(doctorId)
    task
      .then(() => {
        this.setData({
          favorited: next,
          favBusy: false
        })
        toast.success(next ? '已收藏' : '已取消收藏')
      })
      .catch((err) => {
        toast(err.message || '操作失败')
        this.setData({
          favBusy: false
        })
      })
  }
})