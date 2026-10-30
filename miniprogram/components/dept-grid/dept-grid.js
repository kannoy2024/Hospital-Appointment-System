Component({
  properties: {
    list: { type: Array, value: [] }
  },
  methods: {
    // 点击某个科室后向父组件抛出 select 事件并带上科室 id
    onTap(e) {
      const { id } = e.currentTarget.dataset
      this.triggerEvent('select', { id })
    }
  }
})
