Component({
  properties: {
    placeholder: { type: String, value: '搜索科室、医生' },
    value: { type: String, value: '' }
  },
  observers: {
    // 监听 value 属性变化，同步输入框内部的值
    value(val) {
      this.setData({ inputValue: val || '' })
    }
  },
  data: {
    inputValue: ''
  },
  methods: {
    // 输入内容变化时同步输入框的值并向父组件抛出 change 事件
    onInput(e) {
      const value = e.detail.value
      this.setData({ inputValue: value })
      this.triggerEvent('change', { value })
    },
    // 键盘确认搜索时向父组件抛出 search 事件
    onConfirm(e) {
      this.triggerEvent('search', { value: e.detail.value })
    },
    // 点击搜索按钮时用当前输入值向父组件抛出 search 事件
    onSearchTap() {
      this.triggerEvent('search', { value: this.data.inputValue })
    }
  }
})
