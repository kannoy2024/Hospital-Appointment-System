const { baseUrl } = require('./config')

const TOKEN_KEY = 'token'

// 从本地存储读取 token
function getToken() {
  try {
    return wx.getStorageSync(TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

// 封装 wx.request 为 Promise，统一处理 token、401、错误码与网络异常
function request(options) {
  return new Promise((resolve, reject) => {
    const token = options.skipAuth ? '' : getToken()
    wx.request({
      url: baseUrl + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.header || {})
      },
      // wx.request 成功回调：按状态码/业务码统一分类处理后 resolve 或 reject
      success(res) {
        const body = res.data
        if (res.statusCode === 401) {
          try {
            require('./auth').sessionExpired()
          } catch (e) {
            // ignore
          }
          reject(new Error((body && body.message) || '未登录或登录已过期'))
          return
        }
        if (res.statusCode >= 500 && (!body || typeof body !== 'object' || body.code == null)) {
          reject(new Error('后端未启动'))
          return
        }
        if (!body || body.code !== 200) {
          reject(new Error((body && body.message) || '请求失败'))
          return
        }
        resolve(body.data)
      },
      // wx.request 失败回调：把网络错误转成友好的中文提示
      fail(err) {
        const msg = err.errMsg || '网络错误'
        if (msg.indexOf('url not in domain list') !== -1) {
          reject(new Error('域名未配置：开发阶段请勾选不校验合法域名'))
          return
        }
        if (msg.indexOf('connect') !== -1 || msg.indexOf('timeout') !== -1 || msg.indexOf('fail') !== -1) {
          reject(new Error('后端未启动'))
          return
        }
        reject(new Error(msg))
      }
    })
  })
}

module.exports = { request, getToken }
