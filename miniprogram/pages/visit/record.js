const api = require('../../utils/api')
const toast = require('../../utils/toast')

Page({
  data: {
    items: [],
    loading: true
  },

  // 页面加载时根据预约 id 拉取就诊病历并渲染
  onLoad(options) {
    const id = options.id
    if (!id) {
      toast('参数错误')
      return
    }
    api.getConsultation(id)
      .then((record) => {
        if (!record) {
          toast('暂无病历')
          return
        }
        const items = [
          { label: '主诉', value: record.chiefComplaint || '-' },
          { label: '现病史', value: record.presentIllness || '-' },
          { label: '诊断', value: record.diagnosis || '-' },
          { label: '治疗方案', value: record.treatmentPlan || '-' },
          { label: '医嘱', value: record.doctorAdvice || '-' }
        ]
        this.setData({ items, loading: false })
      })
      .catch((err) => {
        this.setData({ loading: false })
        toast(err.message || '加载失败')
      })
  }
})
