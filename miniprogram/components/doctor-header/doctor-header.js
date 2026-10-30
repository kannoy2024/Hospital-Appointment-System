Component({
  properties: {
    doctor: { type: Object, value: {} },
    favorited: { type: null, value: null }  //收藏功能
  },
  methods: {
    onFavTap() {
      this.triggerEvent('togglefav')
    }
  },
  observers: {
    // 监听 doctor 属性变化，优先取属性中的科室名，否则按科室 id 查表得到名称
    doctor(d) {
      if (!d) return
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
  data: { deptName: '' }
})
