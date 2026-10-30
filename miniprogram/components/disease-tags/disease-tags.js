Component({
  properties: {
    list: { type: Array, value: [] }
  },
  methods: {
    // 点击某个疾病标签后向父组件抛出 select 事件并带上疾病名称
    onTap(e) {
      const { name } = e.currentTarget.dataset
      this.triggerEvent('select', { name })
    }
  }
})
