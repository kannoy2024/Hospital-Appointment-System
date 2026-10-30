Page({
  data: {
    symptom: '',
    loading: false,
    quickTags: ['头痛', '发热', '咳嗽', '腹痛', '皮疹', '失眠']
  },

  // 页面显示时触发，若全局暂存了症状则回填到输入框
  onShow() {
    const app = getApp()
    if (app.globalData.triageSymptom) {
      this.setData({ symptom: app.globalData.triageSymptom })
      app.globalData.triageSymptom = ''
    }
  },

  // 症状输入框内容变化时同步到 data
  onSymptomChange(e) {
    this.setData({ symptom: e.detail.value })
  },

  // 点击快捷标签时把标签追加到症状描述中（超长则提示）
  onQuickTag(e) {
    const tag = e.currentTarget.dataset.tag
    const { symptom } = this.data
    const next = symptom ? `${symptom}、${tag}` : tag
    if (next.length > 200) {
      wx.showToast({ title: '描述过长', icon: 'none' })
      return
    }
    this.setData({ symptom: next })
  },

  // 校验症状描述并调用智能分诊接口，结果存入全局后跳转结果页
  onAnalyze() {
    const { symptom, loading } = this.data
    if (loading) return
    const text = symptom.trim()
    if (!text) {
      wx.showToast({ title: '请描述您的症状', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    const api = require('../../utils/api')
    api.triageAnalyze(text)
      .then((result) => {
        const app = getApp()
        app.globalData.triageResult = { ...result, symptom: text }
        wx.navigateTo({ url: '/pages/triage/result' })
      })
      .catch((err) => {
        wx.showToast({ title: err.message || '分析失败', icon: 'none' })
      })
      .finally(() => {
        this.setData({ loading: false })
      })
  }
})
