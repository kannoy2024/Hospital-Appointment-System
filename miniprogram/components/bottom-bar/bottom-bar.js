Component({
  properties: {
    text: { type: String, value: '确定' },
    disabled: { type: Boolean, value: false },
    loading: { type: Boolean, value: false },
    price: { type: String, value: '' },
    priceLabel: { type: String, value: '' },
    aboveTab: { type: Boolean, value: false }
  },
  methods: {
    // 点击底部主按钮时（非禁用非加载中）向父组件抛出 confirm 事件
    onTap() {
      if (this.data.disabled || this.data.loading) return
      this.triggerEvent('confirm')
    }
  }
})
