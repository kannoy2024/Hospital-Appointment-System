/**
 * 金额工具：前端唯一的「元 / 分」换算入口。
 *
 * 约定（与后端 com.system.common.MoneyUtils 一致）：
 *   所有在前后端之间传输、以及在业务里做加减的金额，一律是以「分」为单位的整数，
 *   字段名统一带 Fen 后缀（balanceFen / amountFen / totalAmountFen ...）；
 *   只有最终渲染到界面上时才转成「元」的文本。
 *
 * 命名规则：每个函数名都同时点明「入参单位 → 出参单位」，避免历史上 formatFee(amount)
 * 那种「既不知道传元还是传分、也不知道返回带不带符号」的歧义。
 *   yuanToFen(元)       -> 分（整数）
 *   fenToYuan(分)       -> 元（Number，只用于确实需要数值比较的场景）
 *   fenToYuanText(分)   -> '20.00'（元文本，不带货币符号，可直接喂给 pay-drawer 的 amount）
 *   fenToYuanLabel(分)  -> '¥20.00'（元文本，带货币符号，用于界面展示）
 *   isValidFen(分)      -> 是否为正整数分
 *
 * 不要用 parseFloat / Number(x) * 100 做换算：
 *   Number('0.07') * 100 === 7.000000000000001
 *   1.005 * 100          === 100.49999999999999  （四舍五入后是 100，而后端 HALF_UP 要求 101）
 * 所以下面全程只做字符串拆分与整数运算。
 */

// 单次充值上限（单位：分）。与后端
// springboot/src/main/resources/application.yml 的 app.recharge.max-fen 保持一致，
// 改任意一边都必须同步改另一边，否则会出现「前端放行、后端 400」或反之的错配。
const MAX_RECHARGE_FEN = 1000000

// 挂号费（单位：分）。后端 DoctorVO 里没有费用字段，前端原先在 adapter.enrichDoctor 里
// 硬编码了 20（当时是元），这里显式改成「分」并集中到一处，避免又多一个魔法数字。
const REGISTRATION_FEE_FEN = 2000

// 元 -> 分。入参可以是数字或字符串，非法输入返回 0。
// 按 HALF_UP（第三位小数 >= 5 则进位）处理，与后端 MoneyUtils.yuanToFen 的
// RoundingMode.HALF_UP 对齐。
function yuanToFen(input) {
  const text = String(input == null ? '' : input).trim()
  if (!/^\d+(\.\d*)?$|^\.\d+$/.test(text)) return 0
  const [intPart = '', decPart = ''] = text.split('.')
  const intFen = Number(intPart || '0') * 100
  // 多取一位小数用于四舍五入
  const cents = (decPart + '000').slice(0, 3)
  let decFen = Number(cents.slice(0, 2))
  if (Number(cents.charAt(2)) >= 5) decFen += 1
  return intFen + decFen
}

// 分 -> 元（Number）。仅用于需要数值的地方，展示请用 fenToYuanText
function fenToYuan(fen) {
  return Number(fen || 0) / 100
}

// 分 -> 元文本（固定两位小数，不带货币符号）。纯整数运算，不经过浮点。
function fenToYuanText(fen) {
  const value = Math.trunc(Number(fen) || 0)
  const sign = value < 0 ? '-' : ''
  const abs = Math.abs(value)
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

// 分 -> 元文本（带 ¥ 符号）
function fenToYuanLabel(fen) {
  return `¥${fenToYuanText(fen)}`
}

// 判断一个金额（分）是否为正整数
function isValidFen(fen) {
  return Number.isInteger(fen) && fen > 0
}

module.exports = {
  MAX_RECHARGE_FEN,
  REGISTRATION_FEE_FEN,
  yuanToFen,
  fenToYuan,
  fenToYuanText,
  fenToYuanLabel,
  isValidFen
}
