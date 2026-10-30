const { getDepartments } = require('./departments')

const rules = [
  { keywords: ['头痛', '头晕', '失眠', '麻木'], departmentIds: [1, 12], symptoms: ['头痛', '头晕', '肢体麻木'] },
  { keywords: ['发热', '咳嗽', '感冒', '发烧', '咽痛'], departmentIds: [1, 10], symptoms: ['发热', '咳嗽', '咽痛'] },
  { keywords: ['腹痛', '腹泻', '恶心', '胃'], departmentIds: [1, 2], symptoms: ['腹痛', '腹泻', '消化不良'] },
  { keywords: ['骨折', '关节', '腰痛', '扭伤'], departmentIds: [4, 11], symptoms: ['关节疼痛', '活动受限', '腰痛'] },
  { keywords: ['视力', '眼睛', '眼干'], departmentIds: [5], symptoms: ['视力下降', '眼干', '视物模糊'] },
  { keywords: ['皮疹', '瘙痒', '过敏'], departmentIds: [7, 3], symptoms: ['皮疹', '瘙痒', '过敏'] },
  { keywords: ['牙', '口腔'], departmentIds: [6], symptoms: ['牙痛', '牙龈出血'] },
  { keywords: ['尿频', '尿痛'], departmentIds: [9], symptoms: ['尿频', '尿痛', '排尿不适'] }
]

// 按关键词规则匹配症状并返回推荐科室
function analyzeSymptoms(text) {
  const input = (text || '').trim()
  if (!input) {
    return { departments: getDepartments().slice(0, 3).map((d, i) => ({ ...d, score: 3 - i })), matched: false }
  }

  const depts = getDepartments()
  const scores = {}

  rules.forEach((rule) => {
    const hit = rule.keywords.some((kw) => input.includes(kw))
    if (hit) {
      rule.departmentIds.forEach((id, i) => {
        scores[id] = (scores[id] || 0) + (3 - i)
      })
    }
  })

  let results = Object.keys(scores)
    .map((id) => {
      const dept = depts.find((d) => d.id === Number(id))
      return dept ? { ...dept, score: Math.min(5, scores[id] + 2), symptoms: rules.find((r) => r.departmentIds.includes(dept.id))?.symptoms || [] } : null
    })
    .filter(Boolean)
    .sort((a, b) => b.score - a.score)

  if (!results.length) {
    results = [{ ...depts[11], score: 3, symptoms: ['乏力', '不适'] }]
  }

  return { departments: results.slice(0, 3), matched: true }
}

module.exports = { rules, analyzeSymptoms }
