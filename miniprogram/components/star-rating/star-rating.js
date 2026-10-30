Component({
  properties: {
    value: { type: Number, value: 5 },
    readonly: { type: Boolean, value: false },
    size: { type: Number, value: 20 }
  },
  methods: {
    // 星级评分变化时向父组件抛出 change 事件，只读模式下不响应
    onChange(e) {
      if (this.data.readonly) return
      this.triggerEvent('change', { value: e.detail })
    }
  }
})
