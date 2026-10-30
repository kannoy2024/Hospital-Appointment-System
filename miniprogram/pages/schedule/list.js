const api = require('../../utils/api')
const { enrichDepartments, enrichDoctor } = require('../../utils/adapter')
const { setTabBar } = require('../../utils/tab')
const {
  findDepartmentByKeyword,
  filterDepartmentsByKeyword,
  buildDoctorQuery
} = require('../../utils/search')
const toast = require('../../utils/toast')

Page({
  data: {
    departments: [],
    allDepartments: [],
    activeDeptId: 1,
    doctors: [],
    keyword: '',
    isGlobalSearch: false,
    loading: true
  },

  // 页面加载时触发，解析路由参数中的科室/关键字并加载科室列表
  onLoad(options) {
    const keyword = options.keyword ? decodeURIComponent(options.keyword) : ''
    const deptId = Number(options.deptId)
    const updates = { keyword }
    if (deptId) updates.activeDeptId = deptId
    this.setData(updates)
    this.loadDepartments()
    this._reqSeq = 0
    this.loadDepartments()
  },

  // 页面显示时触发，同步 tabBar 选中态并应用全局待处理的跳转参数
  onShow() {
    setTabBar(this, '/pages/schedule/list')
    this.applyPendingTabParams()
  },

  // 读取全局暂存的科室/关键字参数并刷新列表显示
  applyPendingTabParams() {
    const app = getApp()
    const { pendingDeptId, pendingKeyword } = app.globalData
    const updates = {}

    if (pendingDeptId) {
      updates.activeDeptId = pendingDeptId
      app.globalData.pendingDeptId = 0
    }
    if (pendingKeyword) {
      updates.keyword = pendingKeyword
      app.globalData.pendingKeyword = ''
    }

    if (!Object.keys(updates).length) return

    this.setData(updates, () => {
      if (this.data.allDepartments.length) {
        this.syncDepartmentsForKeyword()
        this.loadDoctors()
      } else {
        this._pendingReload = true
      }
    })
  },

  // 加载全部科室，并按关键字过滤后自动选中匹配科室
  loadDepartments() {
    api.getDepartments()
      .then((departments) => {
        const list = enrichDepartments(departments)
        let activeDeptId = this.data.activeDeptId || (list[0] && list[0].id)
        const keyword = (this.data.keyword || '').trim()
        const deptMatch = findDepartmentByKeyword(list, keyword)
        if (deptMatch) activeDeptId = deptMatch.id

        const displayList = keyword
          ? filterDepartmentsByKeyword(list, keyword)
          : list

        this.setData({
          allDepartments: list,
          departments: displayList.length ? displayList : list,
          activeDeptId
        }, () => {
          if (this._pendingReload) {
            this._pendingReload = false
            this.syncDepartmentsForKeyword()
          }
          this.loadDoctors()
        })
      })
      .catch((err) => {
        this.setData({ loading: false })
        toast(err.message || '加载科室失败')
      })
  },

  // 根据当前关键字重新计算要显示的科室列表
  syncDepartmentsForKeyword() {
    const keyword = (this.data.keyword || '').trim()
    const { allDepartments } = this.data
    if (!keyword) {
      this.setData({ departments: allDepartments })
      return
    }
    const displayList = filterDepartmentsByKeyword(allDepartments, keyword)
    this.setData({
      departments: displayList.length ? displayList : allDepartments
    })
  },

  // 按当前科室/关键字查询医生列表，isSwitch 表示切换科室时无需显示 loading
  loadDoctors(isSwitch = false) {
    const { allDepartments, activeDeptId, keyword } = this.data
    const { params, deptMatch, isGlobalSearch } = buildDoctorQuery({
      departments: allDepartments,
      activeDeptId,
      keyword,
      hasAvailableSlot: true
    })

    const updates = { isGlobalSearch }
    if (deptMatch && deptMatch.id !== activeDeptId) {
      updates.activeDeptId = deptMatch.id
    }

    if (!isSwitch) {
      updates.loading = true
    }
    this.setData(updates)

    api.getDoctors(params)
      .then((doctors) => {
        this.setData({
          doctors: (doctors || []).map((d) => enrichDoctor(d, true)),
          loading: false
        })
      })
      .catch((err) => {
        this.setData({ loading: false })
        toast(err.message || '加载医生失败')
      })
  },

  // 搜索框输入时实时更新关键字
  onSearchChange(e) {
    this.setData({ keyword: e.detail.value || '' })
  },

  // 提交搜索：同步科室显示、尝试匹配科室并重新加载医生
  onSearch(e) {
    const keyword = (e.detail.value || '').trim()
    this.setData({ keyword }, () => {
      this.syncDepartmentsForKeyword()
      const deptMatch = findDepartmentByKeyword(this.data.allDepartments, keyword)
      if (deptMatch) {
        this.setData({ activeDeptId: deptMatch.id }, () => this.loadDoctors())
        return
      }
      this.loadDoctors()
    })
  },

  // 切换科室时清空关键字并重新加载该科室医生
  onDeptChange(e) {
    this.setData({ activeDeptId: e.detail.id, keyword: '' }, () => {
      this.syncDepartmentsForKeyword()
      this.loadDoctors(false)
    })
  },

  // 点击医生卡片时跳转到医生排班详情页
  onDoctorTap(e) {
    const { id } = e.detail
    wx.navigateTo({ url: `/pages/schedule/detail?doctorId=${id}` })
  }
})
