const { getStatusBarHeight } = require('../../utils/system')

const { setTabBar, goDeptTab } = require('../../utils/tab')

const api = require('../../utils/api')

const { enrichDepartments, enrichDoctor } = require('../../utils/adapter')

const toast = require('../../utils/toast')



Page({

  data: {
    statusBarHeight: 20,
    keyword: '',
    departments: [],
    doctors: []
  },



  // 页面加载时触发，设置状态栏高度并拉取首页数据
  onLoad() {

  },



  // 并发加载科室列表与热门医生（仅取有号源的前 3 位）
  loadData() {
    Promise.all([
      api.getDepartments(),
      api.getDoctors({ hasAvailableSlot: true })
    ])
      .then(([departments, doctors]) => {
        this.setData({
          departments: enrichDepartments(departments),
          doctors: (doctors || []).map((d) => enrichDoctor(d, true)).slice(0, 3)
        })
      })
      .catch((err) => toast(err.message || '加载失败'))
  },



  // 页面显示时触发，同步 tabBar 选中态并清空搜索关键字
  onShow() {
    this.setData({ statusBarHeight: getStatusBarHeight() })
    this.loadData()
    
    setTabBar(this, '/pages/index/index')
    this.setData({ keyword: '' })
  },



  // 搜索框输入时实时更新关键字
  onSearchChange(e) {
    this.setData({ keyword: e.detail.value || '' })
  },



  // 提交搜索，携带关键字跳转到科室/医生列表页
  onSearch(e) {
    const keyword = (e.detail.value || '').trim()
    goDeptTab({ keyword })
  },



  // 点击科室卡片时按科室 id 跳转到列表页
  onDeptSelect(e) {
    const { id } = e.detail
    goDeptTab({ deptId: id })
  },



  // 点击医生卡片时跳转到医生排班详情页
  onDoctorTap(e) {
    const { id } = e.detail
    wx.navigateTo({ url: `/pages/schedule/detail?doctorId=${id}` })
  },



  // 点击“更多科室”时跳转到科室列表页
  onMoreDept() {
    goDeptTab()
  },



  // 点击“更多医生”时跳转到医生列表页
  onMoreDoctor() {
    goDeptTab()
  }

})


