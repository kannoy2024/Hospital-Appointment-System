const { baseUrl } = require('./config')

// 将相对媒体路径拼成完整可访问 URL，已是 http(s) 绝对地址则原样返回
function resolveMediaUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  return `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`
}

module.exports = { resolveMediaUrl }
