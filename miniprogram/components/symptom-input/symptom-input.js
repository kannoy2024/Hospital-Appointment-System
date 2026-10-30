Component({
  properties: {
    value: { type: String, value: '' },
    maxLength: { type: Number, value: 200 }
  },
  methods: {
    // 输入症状内容变化时向父组件抛出 change 事件
    onInput(e) {
      this.triggerEvent('change', { value: e.detail.value })
    }
  }
})
