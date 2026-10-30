const STORAGE_KEY = 'hospital_orders'

const defaultOrders = [
  {
    id: '1001',
    orderNo: 'AP20260702001',
    doctorId: 1,
    doctorName: '张明华',
    doctorTitle: '主任医师',
    departmentId: 1,
    departmentName: '内科',
    scheduleDate: '2026-07-05',
    timeSlot: '09:00',
    patientName: '张三',
    patientPhone: '13800138000',
    fee: 25,
    status: 'pending',
    location: '门诊楼3楼 内科诊区',
    createTime: '2026-07-02 10:30:00',
    evaluated: false
  },
  {
    id: '1002',
    orderNo: 'AP20260628002',
    doctorId: 5,
    doctorName: '陈美玲',
    doctorTitle: '副主任医师',
    departmentId: 3,
    departmentName: '儿科',
    scheduleDate: '2026-06-28',
    timeSlot: '14:30',
    patientName: '张三',
    patientPhone: '13800138000',
    fee: 20,
    status: 'completed',
    location: '门诊楼2楼 儿科诊区',
    createTime: '2026-06-25 15:20:00',
    evaluated: false
  }
]

// 从本地存储读取订单（无则写入并返回默认演示数据）
function loadOrders() {
  try {
    const stored = wx.getStorageSync(STORAGE_KEY)
    if (stored && stored.length) return stored
  } catch (e) {
    // ignore
  }
  wx.setStorageSync(STORAGE_KEY, defaultOrders)
  return defaultOrders
}

// 把订单写回本地存储
function saveOrders(orders) {
  wx.setStorageSync(STORAGE_KEY, orders)
}

// 按状态筛选订单并按创建时间倒序返回
function getOrders(filter = {}) {
  let list = loadOrders()
  if (filter.status && filter.status !== 'all') {
    if (filter.status === 'pending') {
      list = list.filter((o) => o.status === 'pending' || o.status === 'confirmed')
    } else {
      list = list.filter((o) => o.status === filter.status)
    }
  }
  return list.sort((a, b) => (b.createTime > a.createTime ? 1 : -1))
}

// 按 id 查找订单
function getOrderById(id) {
  return loadOrders().find((o) => o.id === String(id))
}

// 新增订单并置于列表最前
function addOrder(order) {
  const orders = loadOrders()
  orders.unshift(order)
  saveOrders(orders)
  return order
}

// 按 id 局部更新订单字段
function updateOrder(id, patch) {
  const orders = loadOrders()
  const idx = orders.findIndex((o) => o.id === String(id))
  if (idx === -1) return null
  orders[idx] = { ...orders[idx], ...patch }
  saveOrders(orders)
  return orders[idx]
}

// 取消订单（把状态改为 cancelled）
function cancelOrder(id) {
  return updateOrder(id, { status: 'cancelled' })
}

module.exports = {
  loadOrders,
  getOrders,
  getOrderById,
  addOrder,
  updateOrder,
  cancelOrder
}
