Component({
  properties: {
    doctor: { type: Object, value: {} },
    showDept: { type: Boolean, value: true },
    showStatus: { type: Boolean, value: true },   // 新增，默认 true = 保持现有行为
    showFav: { type: Boolean, value: false }      // 新增，默认 false = 首页/列表页不受影响
  },
  observers: {
    // 监听 doctor 属性变化，按需派生展示用的科室名称
    doctor(d) {
      if (!d || !this.data.showDept) return
      if (d.departmentName) {
        this.setData({ deptName: d.departmentName })
        return
      }
      if (!d.departmentId) return
      const { getDepartmentById } = require('../../mock/departments')
      const dept = getDepartmentById(d.departmentId)
      this.setData({ deptName: dept ? dept.name : '' })
    }
  },
  data: { deptName: '' },
  methods: {
    // 点击医生卡片时向父组件抛出 tap 事件并带上医生 id
    onTap() {
      this.triggerEvent('tap', { id: this.data.doctor.id })
    },
      // 点击收藏图标：wxml 用 catchtap，阻止冒泡到卡片，不会触发跳转
    onFavTap() {
      this.triggerEvent('unfav', { id: this.data.doctor.id })
    }
  }
})
