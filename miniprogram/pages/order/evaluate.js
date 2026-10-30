const api = require('../../utils/api')
const { toOrder } = require('../../utils/adapter')
const toast = require('../../utils/toast')

Page({
  data: {
    order: null,
    rating: 5,
    selectedTags: [],
    comment: '',
    submitting: false
  },

  // 页面加载时校验订单状态，仅已完成且未评价的订单可评价
  onLoad(options) {
    if (!options.id) {
      wx.showToast({ title: '订单不存在', icon: 'none' })
      return
    }
    api.getAppointmentById(options.id)
      .then((appt) => {
        const order = toOrder(appt)
        if (order.status !== 'completed') {
          wx.showToast({ title: '仅已完成订单可评价', icon: 'none' })
          setTimeout(() => wx.navigateBack(), 1500)
          return
        }
        if (order.evaluated) {
          wx.showToast({ title: '您已评价过', icon: 'none' })
          setTimeout(() => wx.navigateBack(), 1500)
          return
        }
        this.setData({ order })
      })
      .catch((err) => {
        toast(err.message || '订单不存在')
        setTimeout(() => wx.navigateBack(), 1500)
      })
  },

  // 拖动星级评分时更新评分值
  onRatingChange(e) {
    this.setData({ rating: e.detail.value })
  },

  // 勾选或取消评价标签时更新选中标签
  onTagsChange(e) {
    this.setData({ selectedTags: e.detail.selected })
  },

  // 输入评价内容时更新评论文本
  onCommentInput(e) {
    this.setData({ comment: e.detail })
  },

  // 提交评价（评分、标签、评论）并在成功后返回上一页
  onSubmit() {
    if (this.data.submitting || !this.data.order) return
    this.setData({ submitting: true })
    api.submitAppointmentReview(this.data.order.id, {
      rating: this.data.rating,
      tags: this.data.selectedTags,
      comment: this.data.comment
    })
      .then(() => {
        wx.showToast({ title: '评价成功', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 800)
      })
      .catch((err) => toast(err.message || '提交失败'))
      .finally(() => this.setData({ submitting: false }))
  }
})
