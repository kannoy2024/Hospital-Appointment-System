Component({
  properties: {
    list: { type: Array, value: [] },
    activeId: { type: Number, value: 0 }
  },
  methods: {
    // 点击左侧科室项，向父组件抛出 change 事件并传回数字型科室 id
    onSelect(e) {
      const { id } = e.currentTarget.dataset
      this.triggerEvent('change', { id: Number(id) })
    }
  }
})
