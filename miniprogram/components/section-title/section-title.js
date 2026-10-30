Component({
  properties: {
    title: { type: String, value: '' },
    more: { type: Boolean, value: false }
  },
  methods: {
    // 点击"更多"按钮时向父组件抛出 more 事件
    onMore() {
      this.triggerEvent('more')
    }
  }
})
