const DEPT_META = {
  内科: { icon: '/assets/dept/neike.png', color: '#00B894' },
  外科: { icon: '/assets/dept/waike.png', color: '#008B84' },
  儿科: { icon: '/assets/dept/erke.png', color: '#22C55E' },
  骨科: { icon: '/assets/dept/guke.png', color: '#00C7A7' },
  眼科: { icon: '/assets/dept/yanke.png', color: '#36CFC9' },
  口腔: { icon: '/assets/dept/kouqiangke.png', color: '#13C2C2' },
  皮肤: { icon: '/assets/dept/pifuke.png', color: '#52C41A' },
  妇科: { icon: '/assets/dept/fuchanke.png', color: '#EB2F96' },
  泌尿: { icon: '/assets/dept/miniaoke.png', color: '#1890FF' },
  传染: { icon: '/assets/dept/chuanranke.png', color: '#FA8C16' },
  康复: { icon: '/assets/dept/kangfuke.png', color: '#722ED1' },
  全科: { icon: '/assets/dept/quanke.png', color: '#666693' }
}

const DEFAULT_META = { icon: '/assets/dept/quanke.png', color: '#00B894' }

// 按科室名获取图标与主题色配置，未匹配时返回默认配置
function getDeptMeta(name) {
  return DEPT_META[name] || DEFAULT_META
}

// 为单个科室对象补充 icon 与 color 字段
function enrichDepartment(dept) {
  const meta = getDeptMeta(dept.name)
  return { ...dept, icon: meta.icon, color: meta.color }
}

// 批量为科室列表补充 icon 与 color 字段
function enrichDepartments(list) {
  return (list || []).map(enrichDepartment)
}

module.exports = { getDeptMeta, enrichDepartment, enrichDepartments }
