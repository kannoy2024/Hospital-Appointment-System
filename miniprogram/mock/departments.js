const departments = [
  { id: 1, name: '内科', icon: '/assets/dept/neike.png', color: '#00B894', description: '呼吸、消化、内分泌等' },
  { id: 2, name: '外科', icon: '/assets/dept/waike.png', color: '#008B84', description: '普外、微创等' },
  { id: 3, name: '儿科', icon: '/assets/dept/erke.png', color: '#22C55E', description: '儿童常见病' },
  { id: 4, name: '骨科', icon: '/assets/dept/guke.png', color: '#00C7A7', description: '骨关节疾病' },
  { id: 5, name: '眼科', icon: '/assets/dept/yanke.png', color: '#36CFC9', description: '视力与眼病' },
  { id: 6, name: '口腔', icon: '/assets/dept/kouqiangke.png', color: '#13C2C2', description: '口腔疾病' },
  { id: 7, name: '皮肤', icon: '/assets/dept/pifuke.png', color: '#52C41A', description: '皮肤病诊疗' },
  { id: 8, name: '妇科', icon: '/assets/dept/fuchanke.png', color: '#EB2F96', description: '妇科常见病' },
  { id: 9, name: '泌尿', icon: '/assets/dept/miniaoke.png', color: '#1890FF', description: '泌尿系统' },
  { id: 10, name: '传染', icon: '/assets/dept/chuanranke.png', color: '#FA8C16', description: '感染性疾病' },
  { id: 11, name: '康复', icon: '/assets/dept/kangfuke.png', color: '#722ED1', description: '康复理疗' },
  { id: 12, name: '全科', icon: '/assets/dept/quanke.png', color: '#666693', description: '综合诊疗' }
]

// 返回全部科室列表
function getDepartments() {
  return departments
}

// 按 id 查找科室
function getDepartmentById(id) {
  return departments.find((d) => d.id === Number(id))
}

module.exports = { departments, getDepartments, getDepartmentById }
