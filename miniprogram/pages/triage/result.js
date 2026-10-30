const { enrichDepartments } = require('../../utils/dept-meta')
const { goDeptTab } = require('../../utils/tab')
const api = require('../../utils/api')

Page({
  data: {
    loading: true,
    symptom: '',
    summary: '',
    symptomAnalysis: '',
    commonSigns: [],
    preliminaryConclusion: '',
    advice: '',
    urgent: false,
    source: '',
    sourceLabel: '',
    departments: []
  },

  // 页面加载时触发，优先使用全局缓存的分诊结果，否则按症状参数重新分析
  onLoad(options) {
    const app = getApp()
    const cached = app.globalData.triageResult
    if (cached && cached.departments) {
      app.globalData.triageResult = null
      this.applyResult(cached)
      return
    }

    const symptom = decodeURIComponent(options.symptom || '')
    if (symptom) {
      this.setData({ symptom })
      this.fetchAnalyze(symptom)
      return
    }

    this.setData({ loading: false })
    wx.showToast({ title: '请先描述症状', icon: 'none' })
    setTimeout(() => wx.navigateBack(), 1500)
  },

  // 调用分诊接口分析症状并渲染结果
  fetchAnalyze(symptom) {
    this.setData({ loading: true })
    api.triageAnalyze(symptom)
      .then((result) => this.applyResult({ ...result, symptom }))
      .catch((err) => {
        wx.showToast({ title: err.message || '分析失败', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 1500)
      })
      .finally(() => this.setData({ loading: false }))
  },

  // 把分诊结果字段格式化后写入 data 渲染页面
  applyResult(result) {
    const source = result.source || 'fallback'
    this.setData({
      loading: false,
      symptom: result.symptom || '',
      summary: result.summary || '',
      symptomAnalysis: result.symptomAnalysis || result.summary || '',
      commonSigns: result.commonSigns || [],
      preliminaryConclusion: result.preliminaryConclusion || '',
      advice: result.advice || '',
      urgent: !!result.urgent,
      source,
      sourceLabel: source === 'deepseek' ? 'AI 分析' : '智能推荐',
      departments: enrichDepartments(result.departments || [])
    })
  },

  // 点击推荐科室时跳转到该科室的医生列表
  onDeptSelect(e) {
    goDeptTab({ deptId: e.detail.id })
  },

  // 点击“查看全部”时跳转到首个推荐科室的医生列表
  onViewAll() {
    const first = this.data.departments[0]
    if (first) {
      goDeptTab({ deptId: first.id })
    }
  }
})
