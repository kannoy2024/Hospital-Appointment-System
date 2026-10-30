/**
 * Axios 封装：自动附加 JWT、统一解析 Result{code,data}、401 跳转登录。
 * 文件下载请设 responseType: 'blob'（见 downloadFile）。
 *
 * 这是前端与后端通信的核心工具，所有 API 请求都通过这个封装发送。
 *
 * 核心功能：
 * 1. 自动在请求头中添加 JWT Token（Bearer 格式）
 * 2. 统一解析后端返回的 Result 格式（{code, message, data}）
 * 3. 统一处理错误提示（防刷屏）
 * 4. 401 未认证时自动跳转到登录页
 * 5. 区分"后端未启动"、"网络错误"、"业务错误"等不同场景
 */

import axios from 'axios'
import { ElMessage } from 'element-plus'        // Element Plus 的消息提示组件
import { getActivePinia } from 'pinia'           // Pinia 状态管理，用于获取当前 Store 实例
import { getToken, clearAuth } from './auth'     // Token 管理工具（存/取/删）
import { useUserStore } from '@/store/user'      // 用户状态 Store
import { teardownSession } from '@/utils/session'// 清理会话（清除 localStorage 等）
import router from '@/router'                    // Vue Router，用于跳转页面

// ==================== 错误防刷屏机制 ====================

/**
 * 错误提示去重间隔（毫秒）
 * 同一个错误消息在 3 秒内只弹一次，避免多个并发请求刷屏
 */
const ERROR_DEDUPE_MS = 3000

/**
 * 记录最后一次错误提示的内容和时间
 * 用于判断是否需要在短时间内重复显示相同错误
 */
let lastErrorToast = { text: '', at: 0 }

/**
 * 是否正在处理 401 未认证跳转
 * 防止多个 401 响应同时触发多次跳转
 */
let handlingUnauthorized = false

/** HTTP 状态码表 */
const STATUS_TEXT = {
    400: '请求参数有误',
    401: '登录已过期，请重新登录',
    403: '没有权限执行该操作',
    404: '请求的资源不存在',
    405: '请求方式不被允许',
    500: '服务器内部错误',
    502: '网关错误',
    503: '服务暂不可用',
    504: '网关超时'
}

// ==================== 错误提示工具 ====================

/**
 * 显示错误消息（带防刷屏）
 *
 * 设计目的：
 * - 多个请求同时失败时，用户只看到一次错误提示
 * - 避免被一堆错误弹窗淹没
 *
 * @param {string} message 错误消息文本
 */
function showError(message) {
    // 1. 格式化消息
    const text = String(message || '请求失败').trim()
    const now = Date.now()

    // 2. 防刷屏：如果 3 秒内出现过相同的消息，不再重复显示
    if (text === lastErrorToast.text && now - lastErrorToast.at < ERROR_DEDUPE_MS) {
        return
    }

    // 3. 记录本次提示
    lastErrorToast = { text, at: now }

    // 4. 显示错误消息（grouping: true 表示相同消息合并显示）
    ElMessage.error({ message: text, grouping: true })
}

/**
 * 检查响应数据是否包含后端返回的业务消息
 *
 * @param {*} data 响应数据
 * @returns {boolean} 是否包含 message 字段
 */
function hasApiMessage(data) {
    return data && typeof data === 'object' && data.message
}

// ==================== 错误消息解析 ====================

/**
 * 解析错误消息，优先从后端获取，后端没有则生成友好提示
 *
 * 后端未启动时的几种表现：
 * - error.response 不存在（请求根本没发出去）
 * - error.code === 'ECONNREFUSED'（连接被拒绝）
 * - error.message === 'Network Error'（网络错误）
 * - status 502/503/504（网关错误）
 * - status 500 且后端没有返回业务消息（Spring Boot 默认错误页）
 *
 * @param {Error} error Axios 错误对象
 * @returns {string} 用户友好的错误消息
 */
function resolveErrorMessage(error) {
    const status = error.response?.status
    const data = error.response?.data
    const backendMessage = hasApiMessage(data) ? data.message : ''

    // 1 网络层错误，请求根本没有发出去
    if(!error.response||error.code === 'ECONNABORTED' || error.message === 'Network Error') {
    return '网络异常，请检查您的网络连接，确认后端是否启动'
    }

    // 2 HTTP 状态码错误，优先使用后端返回的 message
    if (backendMessage) {
        return `[${status}] ${backendMessage}`
    }

    // 3 后端有相应但是没有message，使用状态码对应的提示
    if(status){
        return `[${status}] ${STATUS_TEXT[status] || '请求失败'}`   
    }

    // 4 兜底提示
    return '请求失败，请稍后重试'||error.message
    
}

// ==================== 401 未认证处理 ====================

/**
 * 处理 401 未认证响应
 *
 * 执行流程：
 * 1. 防止重复跳转（handlingUnauthorized 标志）
 * 2. 清除用户状态（Pinia Store）
 * 3. 清除 Token（localStorage）
 * 4. 跳转到登录页
 *
 * 为什么需要防重复？
 * 如果 3 个接口同时返回 401，会触发 3 次跳转
 * 用标志位确保只跳转一次
 */
function handleUnauthorized() {
    // 1. 如果已经在处理中，直接返回
    if (handlingUnauthorized) return
    handlingUnauthorized = true

    // 2. 获取 Pinia 实例
    const pinia = getActivePinia()

    // 3. 清除用户状态
    if (pinia) {
        useUserStore(pinia).logout()   // 调用 Store 的 logout 方法
        teardownSession()              // 清理会话数据
    } else {
        clearAuth()                    // 降级方案：直接清除 Token
    }

    // 4. 跳转到登录页
    router.push('/login').finally(() => {
        // 500ms 后重置标志位，允许下一次 401 触发
        setTimeout(() => {
            handlingUnauthorized = false
        }, 500)
    })
}

// ==================== 创建 Axios 实例 ====================

/**
 * Axios 实例
 *
 * baseURL: '/api'
 *   - 所有请求都会自动加上 /api 前缀
 *   - 例如：request.get('/users') → GET /api/users
 *   - 配合 Vite 代理，转发到 Spring Boot（如 http://localhost:8080）
 *
 * timeout: 15000
 *   - 请求超时时间 15 秒
 *   - 超过时间仍未响应，自动中断请求
 */
const request = axios.create({
    baseURL: '/api',
    timeout: 15000
})

// ==================== 请求拦截器 ====================

/**
 * 请求拦截器：在请求发出前自动添加 JWT Token
 *
 * 执行流程：
 * 1. 从 localStorage 获取 Token
 * 2. 如果 Token 存在，添加到 Authorization 请求头
 * 3. 格式：Bearer eyJhbGciOiJIUzI1NiJ9...
 *
 * 为什么用 Bearer 前缀？
 * - 这是标准的 JWT 传输格式（RFC 6750）
 * - 后端 JwtAuthenticationFilter 会按此格式解析
 */
request.interceptors.request.use(config => {
    const token = getToken()      // 从 localStorage 获取 Token
    if (token) {
        // 设置请求头：Authorization: Bearer {token}
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// ==================== 响应拦截器 ====================

/**
 * 响应拦截器：统一处理后端响应
 *
 * 两种响应类型：
 *
 * 1. 二进制文件下载（responseType: 'blob'）
 *    - 如果后端返回的是 JSON 错误体（如 400 错误），需要解析并提示
 *    - 如果返回的是文件（如 Excel、图片），直接返回 Blob
 *
 * 2. 普通 JSON 响应
 *    - 检查 code 是否为 200
 *    - 200 → 返回 data 字段（业务数据）
 *    - 非 200 → 显示错误消息，如果是 401 则跳转登录
 */
request.interceptors.response.use(
    // ==================== 成功回调 ====================
    response => {
        // ----- 特殊处理：文件下载（blob） -----
        if (response.config.responseType === 'blob') {
            const blob = response.data

            // 检查 blob 类型：如果是 JSON，说明后端返回的是错误信息
            if (blob.type && blob.type.includes('application/json')) {
                // 读取 blob 内容并解析 JSON
                return blob.text().then(text => {
                    try {
                        const json = JSON.parse(text)
                        showError(json.message || '请求失败')
                    } catch {
                        showError('请求失败')
                    }
                    return Promise.reject(new Error('请求失败'))
                })
            }

            // 正常文件下载，返回 Blob
            return blob
        }

        // ----- 正常 JSON 响应 -----
        const res = response.data

        // 检查业务状态码
        if (res.code !== 200) {
            // 显示错误消息
            showError(res.message || '请求失败')

            // 如果是 401 未认证，触发跳转登录
            if (res.code === 401) {
                handleUnauthorized()
            }

            // 返回 rejected Promise，让调用方 catch 到错误
            return Promise.reject(new Error(res.message || '请求失败'))
        }

        // 成功：返回 data 字段（业务数据）
        // 例如：{ code: 200, message: 'success', data: { users: [...] } }
        // 调用方直接拿到 { users: [...] }，不需要再判断 code
        return res.data
    },

    // ==================== 错误回调 ====================
    /**
     * 网络异常或 HTTP 错误处理
     *
     * 触发场景：
     * - 网络断开
     * - 后端未启动（ECONNREFUSED）
     * - HTTP 4xx/5xx（如 500 内部错误）
     * - 请求超时
     * - 跨域问题
     */
    error => {
        const status = error.response?.status
        const message = resolveErrorMessage(error)

        // 如果是 401，触发跳转登录
        if (status === 401) {
            handleUnauthorized()
        }

        // 显示错误消息
        showError(message)

        // 返回 rejected Promise
        return Promise.reject(error)
    }
)

// ==================== 导出 ====================
export default request