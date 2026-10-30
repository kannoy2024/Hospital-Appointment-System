const {
  request
} = require('./request')
const {
  baseUrl
} = require('./config')

const TOKEN_KEY = 'token'
const USER_KEY = 'user'
const MANUAL_LOGOUT_KEY = 'auth_manual_logout'

// 从本地存储读取登录 token
function getToken() {
  try {
    return wx.getStorageSync(TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

// 从本地存储读取当前登录用户信息
function getUser() {
  try {
    return wx.getStorageSync(USER_KEY) || null
  } catch (e) {
    return null
  }
}

// 保存登录态：写入 token/user 到本地存储与全局数据，并清除手动登出标记
function setAuth(token, user) {
  wx.setStorageSync(TOKEN_KEY, token)
  wx.setStorageSync(USER_KEY, user)
  wx.removeStorageSync(MANUAL_LOGOUT_KEY)
  const app = getApp()
  if (app) {
    app.globalData.token = token
    app.globalData.user = user
  }
}

// 清除登录态：移除本地存储的 token/user 并重置全局数据
function clearAuth() {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(USER_KEY)
  const app = getApp()
  if (app) {
    app.globalData.token = ''
    app.globalData.user = null
  }
}

// 判断当前是否已登录（是否存在 token）
function isLoggedIn() {
  return !!getToken()
}

// 判断是否允许自动登录（用户未主动登出时返回 true）
function shouldAutoLogin() {
  try {
    return !wx.getStorageSync(MANUAL_LOGOUT_KEY)
  } catch (e) {
    return true
  }
}

// 账号密码登录：调用后端登录接口并保存登录态
function passwordLogin(username, password) {
  return request({
    url: '/api/auth/login',
    method: 'POST',
    skipAuth: true,
    data: {
      username,
      password
    }
  }).then((data) => {
    setAuth(data.token, data.user)
    return data
  })
}

// 微信登录：用 wx.login 的 code 换取后端 JWT 并保存登录态，附带资料时同步微信资料
function wxLogin(options = {}) {
  return new Promise((resolve, reject) => {
    wx.login({
      // wx.login 成功回调：用返回的 code 请求后端换取 JWT
      success(loginRes) {
        if (!loginRes.code) {
          reject(new Error('获取微信 code 失败'))
          return
        }
        request({
          url: '/api/auth/wx-login',
          method: 'POST',
          skipAuth: true, //告诉request不要往请求里面放Authorization
          data: {
            code: loginRes.code,
            nickname: options.nickname || '',
            phone: options.phone || ''
          }
        }).then((data) => {
          setAuth(data.token, data.user) //保存登录态
          const avatarPath = options.avatarPath
          const nickname = (options.nickname || '').trim()
          const phone = (options.phone || '').trim()
          if (avatarPath || nickname || phone) {
            syncWxProfile({
                nickname,
                avatarPath,
                phone
              })
              .then((user) => {
                resolve({
                  token: getToken(),
                  user
                })
              })
              .catch(reject)
            return
          }
          resolve(data)
        }).catch(reject)
      },
      // wx.login 失败回调：抛出错误提示
      fail(err) {
        reject(new Error(err.errMsg || 'wx.login 失败'))
      }
    })
  })
}

// 上传头像文件到服务端，返回文件信息
function uploadAvatarFile(filePath) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    if (!token) {
      reject(new Error('请先登录'))
      return
    }
    wx.uploadFile({
      url: `${baseUrl}/api/files/upload`,
      filePath,
      name: 'file',
      formData: {
        category: 'avatar'
      },
      header: {
        Authorization: `Bearer ${token}`
      },
      // wx.uploadFile 成功回调：解析响应并返回文件信息
      success(res) {
        let body
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          reject(new Error('上传响应解析失败'))
          return
        }
        if (res.statusCode === 401) {
          sessionExpired()
          reject(new Error((body && body.message) || '登录已过期'))
          return
        }
        if (!body || body.code !== 200) {
          reject(new Error((body && body.message) || '头像上传失败'))
          return
        }
        resolve(body.data)
      },
      // wx.uploadFile 失败回调：抛出上传失败提示
      fail(err) {
        reject(new Error(err.errMsg || '头像上传失败'))
      }
    })
  })
}

// 将上传后的头像路径绑定到当前用户资料
function bindAvatar(path) {
  return request({
    url: '/api/profile/avatar',
    method: 'PUT',
    data: {
      path
    }
  })
}

// 更新个人资料（昵称/邮箱/手机号/性别），未传字段沿用当前值，成功后刷新登录态
function updateProfile(data) {
  const current = getUser() || {}
  return request({
    url: '/api/profile',
    method: 'PUT',
    data: {
      nickname: data.nickname !== undefined ? data.nickname : (current.nickname || ''),
      email: data.email !== undefined ? data.email : (current.email || ''),
      phone: data.phone !== undefined ? data.phone : (current.phone || '')
    }
  }).then((user) => {
    setAuth(getToken(), user)
    return user
  })
}

// 同步微信资料：按需更新资料并上传/绑定头像，返回最新用户信息
function syncWxProfile({
  nickname,
  avatarPath,
  phone,
  email
} = {}) {
  let chain = Promise.resolve(getUser())
  const trimmedNickname = (nickname || '').trim()
  const trimmedPhone = (phone || '').trim()
  const trimmedEmail = (email || '').trim()
  if (trimmedNickname || trimmedPhone || trimmedEmail) {
    chain = chain.then(() => updateProfile({
      nickname: trimmedNickname || undefined,
      phone: trimmedPhone || undefined,
      email: trimmedEmail || undefined
    }))
  }
  if (avatarPath) {
    chain = chain
      .then(() => uploadAvatarFile(avatarPath))
      .then((fileVo) => bindAvatar(fileVo.path))
      .then((user) => {
        setAuth(getToken(), user)
        return user
      })
  }
  return chain
}

// 拉取当前登录用户信息并刷新本地登录态
function fetchMe() {
  return request({
    url: '/api/auth/me'
  }).then((user) => {
    const token = getToken()
    if (token) {
      setAuth(token, user)
    }
    return user
  })
}

// 主动退出登录：清除登录态并记录手动登出标记（阻止自动登录）
function logout() {
  clearAuth()
  wx.setStorageSync(MANUAL_LOGOUT_KEY, true)
}

// 登录态过期处理：清除本地登录态（由 request 层在 401 时调用）
function sessionExpired() {
  clearAuth()
}

// 未登录时弹窗引导登录；已登录返回 true，未登录返回 false（调用方据此中断）
function ensureLogin(options = {}) {
  if (isLoggedIn()) return true
  const {
    title = '提示',
      content = '该操作需要先登录，是否前往登录？',
      confirmText = '去登录',
      cancelText = '取消'
  } = options
  wx.showModal({
    title,
    content,
    confirmText,
    cancelText,
    success(res) {
      if (res.confirm) {
        wx.switchTab({
          url: '/pages/mine/mine'
        })
      }
    }
  })
  return false
}

module.exports = {
  getToken,
  getUser,
  isLoggedIn,
  shouldAutoLogin,
  passwordLogin,
  wxLogin,
  fetchMe,
  updateProfile,
  syncWxProfile,
  logout,
  sessionExpired,
  ensureLogin
}