const doctors = [
  { id: 1, departmentId: 1, name: '张明华', title: '主任医师', hospital: '市第一人民医院', rating: 4.9, consultCount: 3280, intro: '擅长呼吸系统疾病诊治，从业20年。', avatar: '', fee: 25, hasSlot: true },
  { id: 2, departmentId: 1, name: '李晓芳', title: '副主任医师', hospital: '市第一人民医院', rating: 4.8, consultCount: 2156, intro: '擅长消化内科常见病。', avatar: '', fee: 20, hasSlot: true },
  { id: 3, departmentId: 2, name: '王建国', title: '主任医师', hospital: '市第一人民医院', rating: 4.9, consultCount: 4102, intro: '普外科手术经验丰富。', avatar: '', fee: 30, hasSlot: true },
  { id: 4, departmentId: 2, name: '赵志强', title: '主治医师', hospital: '市第一人民医院', rating: 4.7, consultCount: 986, intro: '擅长微创手术。', avatar: '', fee: 15, hasSlot: false },
  { id: 5, departmentId: 3, name: '陈美玲', title: '副主任医师', hospital: '市第一人民医院', rating: 4.9, consultCount: 5621, intro: '儿科常见病及过敏诊治。', avatar: '', fee: 20, hasSlot: true },
  { id: 6, departmentId: 4, name: '刘德伟', title: '主任医师', hospital: '市第一人民医院', rating: 4.8, consultCount: 2890, intro: '骨关节疾病专家。', avatar: '', fee: 25, hasSlot: true },
  { id: 7, departmentId: 5, name: '周文静', title: '主治医师', hospital: '市第一人民医院', rating: 4.6, consultCount: 1543, intro: '白内障及眼底病。', avatar: '', fee: 15, hasSlot: true },
  { id: 8, departmentId: 12, name: '孙海涛', title: '副主任医师', hospital: '市第一人民医院', rating: 4.7, consultCount: 3201, intro: '全科医学，慢性病管理。', avatar: '', fee: 15, hasSlot: true }
]

// 按科室 / 关键字筛选医生列表
function getDoctors(filter = {}) {
  let list = [...doctors]
  if (filter.departmentId) {
    list = list.filter((d) => d.departmentId === Number(filter.departmentId))
  }
  if (filter.keyword) {
    const kw = filter.keyword.trim().toLowerCase()
    list = list.filter((d) => d.name.includes(kw) || d.title.includes(kw))
  }
  return list
}

// 按 id 查找医生
function getDoctorById(id) {
  return doctors.find((d) => d.id === Number(id))
}

// 推荐有号且评分高的前 N 位医生
function getRecommendedDoctors(limit = 3) {
  return [...doctors]
    .filter((d) => d.hasSlot)
    .sort((a, b) => b.rating - a.rating || b.consultCount - a.consultCount)
    .slice(0, limit)
}

module.exports = { doctors, getDoctors, getDoctorById, getRecommendedDoctors }