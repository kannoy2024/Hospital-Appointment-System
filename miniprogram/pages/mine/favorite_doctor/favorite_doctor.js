// pages/mine/favorite_doctor.js

const api = require('../../../utils/api')
const { enrichDoctor } = require('../../../utils/adapter')
const toast = require('../../../utils/toast')
const auth = require('../../../utils/auth')

Page({

  /**
   * 页面的初始数据
   */
  data: {
    favorites: [],
    loading: true
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {

  },

  /**
   * 生命周期函数--监听页面初次渲染完成
   */
  onReady() {

  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {
    if (!auth.isLoggedIn()) {
      toast('请先登录')
      setTimeout(() => wx.navigateBack(), 800)
      return
    }
    this.loadFavorites()
  },

  loadFavorites() {
    this.setData({ loading: true })
    api.getFavorites()
      .then((list) => {
        this.setData({
          favorites: (list || []).map(this.decorate),
          loading: false
        })
      })
      .catch((err) => {
        toast(err.message || '加载失败')
        this.setData({ favorites: [], loading: false })
      })
  },

  // UserFavoriteDoctorVO -> 卡片能直接渲染的结构
  decorate(item) {
    const doctor = enrichDoctor(item.doctor || {}, true)
    return {
      favoriteId: item.id,
      doctorId: item.doctorId || doctor.id,
      // 收藏列表接口不返回「今日是否有号」，置空让卡片不显示号源标签
      doctor: { ...doctor, hasSlot: null }
    }
  },
  onDoctorTap(e) {
    const { id } = e.detail
    wx.navigateTo({ url: `/pages/schedule/detail?doctorId=${id}` })
  },


  /**
   * 生命周期函数--监听页面隐藏
   */
  onHide() {

  },

  /**
   * 生命周期函数--监听页面卸载
   */
  onUnload() {

  },

  /**
   * 页面相关事件处理函数--监听用户下拉动作
   */
  onPullDownRefresh() {

  },

  /**
   * 页面上拉触底事件的处理函数
   */
  onReachBottom() {

  },

  /**
   * 用户点击右上角分享
   */
  onShareAppMessage() {

  },
  
  onUnfav(e) {
    const doctorId = e.detail.id
    const target = this.data.favorites.find((f) => String(f.doctorId) === String(doctorId))
    if (!target) return
    wx.showModal({
      title: '取消收藏',
      content: `确定取消收藏「${target.doctor.name}」吗？`,
      confirmColor: '#FF4D4F',
      success: (res) => {
        if (!res.confirm) return
        api.removeFavorite(doctorId)
          .then(() => {
            toast.success('已取消收藏')
            this.setData({
              favorites: this.data.favorites.filter(
                (f) => String(f.doctorId) !== String(doctorId)
              )
            })
          })
          .catch((err) => toast(err.message || '操作失败'))
      }
    })
  }
})