const auth = require('../../utils/auth')
const toast = require('../../utils/toast')
const api = require('../../utils/api')
const {
  enrichDoctor
} = require('../../utils/adapter')
const {
  formatDateTime
} = require('../../utils/format')
const money = require('../../utils/money')

//数据列表
Page({
  data: {
    doctor: null,
    deptName: '',
    scheduleDate: '',
    timeSlot: '',
    scheduleId: 0,
    remark: '',
    feeText: '',
    timeText: '',
    submitting: false,
    //修改内容，就诊人相关
    patients: [],
    patientNames: [],
    patientIndex: 0,
    selectedPatient: null, //当前选中的就诊人对象
    patientLoading: true

  },


  // 页面加载时触发，校验登录态、解析号源参数并加载医生信息与就诊人列表
  onLoad(options) {
    if (!auth.isLoggedIn()) {
      wx.showModal({
        title: '提示',
        content: '预约需要先登录，是否前往登录？',
        success: (res) => {
          if (res.confirm) wx.switchTab({
            url: '/pages/mine/mine'
          })
          return
        }
      })
    }
    const doctorId = Number(options.doctorId)
    const scheduleDate = options.scheduleDate || ''
    const timeSlot = options.timeSlot || ''
    const scheduleId = Number(options.scheduleId) || 0
    api.getDoctorById(doctorId)
      .then((doctor) => {
        const enriched = enrichDoctor(doctor, true)
        this.setData({
          doctor: enriched,
          deptName: doctor.departmentName || '',
          scheduleDate,
          timeSlot,
          scheduleId,
          // 挂号费来自 enrichDoctor 的 feeFen（单位分）
          feeText: money.fenToYuanLabel(enriched.feeFen),
          timeText: formatDateTime(scheduleDate, timeSlot)
        })
      })
      .catch((err) => toast(err.message || '加载失败'))
    //加载就诊人列表
    this.loadPatients()
  },

  //提供一个返回本页的刷新功能
  onShow() {
    if (!this.data.patientLoading) {
      this.loadPatients()
    }
  },

  //用户加载就诊人，并选中默认就诊人
  loadPatients() {
    this.setData({
      patientLoading: true
    })
    api.getPatients()
      .then((list) => {
        const patients = list || []
        if (!patients.length) {
          //检查是否空列表
          this.setData({
            patients: [],
            patientNames: [],
            patientIndex: 0,
            selectedPatient: null,
            patientLoading: false
          })
          //提示弹窗
          wx.showModal({
            title: '提示',
            content: '至少添加一名就诊人信息',
            confirmText: '去添加',
            cancelText: '返回',
            success: (res) => {
              if (res.confirm) {
                wx.navigateTo({
                  url: '/pages/mine/patient/edit'
                })
              } else {
                wx.navigateBack()
              }
            }
          })
          return
        }
        //存在就诊人列表，找到默认就诊人，防御性排序：再按照isdefault查一遍
        let idx = patients.findIndex((p) => p.isDefault === 1)
        if (idx < 0) idx = 0
        this.setData({
          patients,
          patientNames: patients.map((p) => p.name),
          patientIndex: idx,
          selectedPatient: patients[idx],
          patientLoading: false
        })
      })
      .catch((err) => {
        this.setData({
          patientLoading: false
        })
        toast(err.message || '加载就诊人失败！')
      })
  },

  // 切换就诊人
  onPatientChange(e) {
    const idx = Number(e.detail.value)
    this.setData({
      patientIndex: idx,
      selectedPatient: this.data.patients[idx] || null
    })
  },

  // 去新增就诊人
  onAddPatient() {
    wx.navigateTo({
      url: '/pages/mine/patient/edit'
    })
  },

  // 备注输入时同步到 remark
  onRemarkInput(e) {
    this.setData({
      remark: e.detail
    })
  },

  // 点击确认预约按钮，提交订单
  onConfirm() {
    this.doSubmitOrder()
  },


  // 只建单(unpaid),不支付;跳转订单详情页完成支付
  async doSubmitOrder() {
    if (this.data.submitting) return
    const {
      scheduleId,
      remark,
      selectedPatient
    } = this.data
   
    if (!auth.isLoggedIn()) {
      toast('请先登录')
      wx.switchTab({
        url: '/pages/mine/mine'
      })
      return
    }
    if (!scheduleId) {
      toast('号源信息缺失，请重新选择')
      return
    }
    this.setData({
      submitting: true
    })
    try {
      const appt = await api.createAppointment({
        scheduleId,
        patientId:selectedPatient.id,
        remark
      })
      // 建单成功(状态 unpaid),跳到订单详情页选择支付方式支付
      wx.redirectTo({
        url: `/pages/order/detail?id=${appt.id}`
      })
    } catch (err) {
      toast(err.message || '下单失败')
    } finally {
      this.setData({
        submitting: false
      })
    }
  }
})