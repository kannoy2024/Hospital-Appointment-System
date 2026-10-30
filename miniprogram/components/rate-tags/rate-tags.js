Component({
  properties: {
    list: {
      type: Array,
      value: ['态度好', '很专业', '耐心细致', '解答清楚', '环境整洁']
    },
    selected: { type: Array, value: [] }
  },
  methods: {
    // 点击标签进行选中/取消选中，并抛出 change 事件同步已选标签数组
    onTap(e) {
      const { tag } = e.currentTarget.dataset
      let selected = [...this.data.selected]
      const idx = selected.indexOf(tag)
      if (idx >= 0) {
        selected.splice(idx, 1)
      } else {
        selected.push(tag)
      }
      this.setData({ selected })
      this.triggerEvent('change', { selected })
    }
  }
})
