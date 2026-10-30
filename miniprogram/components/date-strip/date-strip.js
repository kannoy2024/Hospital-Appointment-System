Component({
  properties: {
    list: { type: Array, value: [] },
    value: { type: String, value: '' }
  },
  methods: {
    // 选择某个日期后向父组件抛出 change 事件并传回日期值
    onSelect(e) {
      const { value } = e.currentTarget.dataset
      this.triggerEvent('change', { value })
    }
  }
})
