Component({
  properties: {
    label: { type: String, value: '' },
    value: { type: String, value: '' },
    placeholder: { type: String, value: '请选择' },
    arrow: { type: Boolean, value: true }
  },
  methods: {
    // 点击整行信息后向父组件抛出 tap 事件
    onTap() {
      this.triggerEvent('tap')
    }
  }
})
