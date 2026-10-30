Component({
  properties: {
    department: { type: Object, value: {} },
    rank: { type: Number, value: 1 }
  },
  methods: {
    // 点击推荐科室卡片后向父组件抛出 select 事件并带上科室 id
    onTap() {
      this.triggerEvent('select', { id: this.data.department.id })
    }
  }
})
