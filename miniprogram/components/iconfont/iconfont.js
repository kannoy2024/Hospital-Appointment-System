const ICON_CHARS = {
  'icon-kangfuke': '\ue81d',
  'icon-waike': '\ue81e',
  'icon-chuanranke': '\ue81f',
  'icon-quanke': '\ue820',
  'icon-erke': '\ue821',
  'icon-miniaoke': '\ue822',
  'icon-kouqiangke': '\ue823',
  'icon-pifuke': '\ue824',
  'icon-guke': '\ue825',
  'icon-fuchanke': '\ue826',
  'icon-yanke': '\ue827',
  'icon-neike': '\ue828'
}

Component({
  properties: {
    name: { type: String, value: '' },
    size: { type: Number, value: 48 },
    color: { type: String, value: '#333333' }
  },
  data: {
    char: ''
  },
  observers: {
    // 监听 name 属性变化，映射为对应图标的 unicode 字符
    name(name) {
      this.setData({ char: ICON_CHARS[name] || '' })
    }
  },
  lifetimes: {
    // 组件挂载时按初始 name 属性设置图标字符
    attached() {
      this.setData({ char: ICON_CHARS[this.properties.name] || '' })
    }
  }
})
