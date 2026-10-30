Component({
  properties: {
    banners: {
      type: Array,
      value: [
        {
          id: 2,
          type: 'image',
          image: '/assets/banner/banner2.svg',
          link: 'dept'
        },
        {
          id: 3,
          type: 'image',
          image: '/assets/banner/banner3.svg',
          link: 'triage'
        }
      ]
    }
  },
  data: { current: 0 },
  methods: {
    // 轮播切换时记录当前所在的下标
    onChange(e) {
      this.setData({ current: e.detail.current })
    },
    // 点击轮播图按 link 类型跳转分诊页或切换到科室tab
    onBannerTap(e) {
      const { link } = e.currentTarget.dataset
      if (link === 'triage') {
        wx.navigateTo({ url: '/pages/triage/index' })
        return
      }
      if (link === 'dept') {
        const { goDeptTab } = require('../../utils/tab')
        goDeptTab()
      }
    }
  }
})
