/**
 * 搜索关键词解析：科室名优先匹配，否则按医生姓名/职称/科室全局检索
 */
// 按关键词匹配科室：优先精确匹配，其次唯一部分匹配，多个时取前缀命中的
function findDepartmentByKeyword(departments, keyword) {
  const kw = (keyword || '').trim()
  if (!kw || !departments || !departments.length) return null

  const exact = departments.find((d) => d.name === kw)
  if (exact) return exact

  const partial = departments.filter(
    (d) => d.name.includes(kw) || kw.includes(d.name)
  )
  if (partial.length === 1) return partial[0]
  if (partial.length > 1) {
    const best = partial.find((d) => d.name.startsWith(kw)) || partial[0]
    return best
  }
  return null
}

// 按关键词过滤科室列表（名称包含关键词）
function filterDepartmentsByKeyword(departments, keyword) {
  const kw = (keyword || '').trim()
  if (!kw) return departments
  return departments.filter((d) => d.name.includes(kw))
}

// 构建医生查询参数：关键词命中科室则按科室查，否则作为全局关键词检索
function buildDoctorQuery({ departments, activeDeptId, keyword, hasAvailableSlot = true }) {
  const kw = (keyword || '').trim()
  const params = { hasAvailableSlot }

  if (!kw) {
    if (activeDeptId) params.departmentId = activeDeptId
    return { params, deptMatch: null, isGlobalSearch: false }
  }

  const deptMatch = findDepartmentByKeyword(departments, kw)
  if (deptMatch) {
    params.departmentId = deptMatch.id
    return { params, deptMatch, isGlobalSearch: false }
  }

  params.keyword = kw
  return { params, deptMatch: null, isGlobalSearch: true }
}

// 在前端本地按姓名/职称/科室过滤医生列表
function filterDoctorsLocally(doctors, keyword) {
  const kw = (keyword || '').trim().toLowerCase()
  if (!kw) return doctors
  return (doctors || []).filter((d) => {
    const name = (d.name || '').toLowerCase()
    const title = (d.title || '').toLowerCase()
    const dept = (d.departmentName || '').toLowerCase()
    return name.includes(kw) || title.includes(kw) || dept.includes(kw)
  })
}

// 在前端本地按名称过滤科室列表
function filterDepartmentsLocally(departments, keyword) {
  const kw = (keyword || '').trim()
  if (!kw) return departments
  return (departments || []).filter((d) => d.name.includes(kw))
}

module.exports = {
  findDepartmentByKeyword,
  filterDepartmentsByKeyword,
  buildDoctorQuery,
  filterDoctorsLocally,
  filterDepartmentsLocally
}
