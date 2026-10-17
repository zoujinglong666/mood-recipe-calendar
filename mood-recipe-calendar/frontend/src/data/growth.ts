/**
 * 增长钩子静态数据：锅仔老黄历 + 干饭人格 / 默契测试。
 *
 * 全部为前端静态内容，无后端依赖；文案保持锅仔 IP 口吻
 * （亲切、口语、贪吃但不油腻，与转盘的「锅仔说话算数」一脉相承）。
 *
 * 两条诚实原则：
 * 1. 老黄历按日期种子轮换——同一天所有人看到同一份「今日宜/忌」，刷新不变，像真的黄历；
 * 2. 人格结果由真实答案计分（argmax）算出，默契度由两人答案逐题比对算出——绝不假随机。
 */

/* ================= 老黄历 ================= */

export const YI_ITEMS = [
  '整顿隔夜菜',
  '慢炖一锅汤',
  '给自己加个蛋',
  '碳水续命',
  '开火露一手',
  '清空冰箱',
  '认真吃早餐',
  '请自己吃顿好的',
  '复刻妈妈的味道',
  '尝一道没吃过的菜',
  '准点干饭',
  '煮一锅饺子',
  '饭后散步消食',
  '把汤熬到浓白',
]

export const JI_ITEMS = [
  '外卖糊弄',
  '边刷手机边干饭',
  '空腹硬扛',
  '深夜炸鸡',
  '拿零食当正餐',
  '剩菜连吃三天',
  '一边叹气一边吃饭',
  '烫到嘴巴还继续',
  '干饭五分钟收尾',
  '对着剩饭发愁',
  '暴饮暴食',
  '冷掉还嘴硬不热',
]

/** 三个签级对应的签解，{dish} 会替换成真实抽到的菜名 */
export const FORTUNE_LINES = [
  '「{dish}」！这签连锅仔都不敢乱摇——今天的灶台属于你，做出来大概率好吃到眯眼。',
  '「{dish}」，稳。食材不复杂、锅气不打折，照着做就行。',
  '「{dish}」，安安稳稳一顿饭。今天不求惊艳，只求吃得舒坦。',
]

export interface AlmanacDay {
  /** 当日种子（YYYYMMDD），签级抖动也复用它 */
  seed: number
  yi: string[]
  ji: string[]
}

/** 按日期确定性抽取：同一天结果恒定 */
export function drawAlmanac(date: Date): AlmanacDay {
  const seed = date.getFullYear() * 10000 + (date.getMonth() + 1) * 100 + date.getDate()
  return {
    seed,
    yi: pickBySeed(YI_ITEMS, seed, 2),
    ji: pickBySeed(JI_ITEMS, seed * 7 + 3, 2),
  }
}

/** 首页 bento 卡的「今日宜」实时文案 */
export function getTodayYi(): string[] {
  return drawAlmanac(new Date()).yi
}

/** 线性同余确定性抽样：只用于文案轮换，不用于任何结果判定 */
function pickBySeed<T>(list: T[], seed: number, count: number): T[] {
  const arr = [...list]
  const out: T[] = []
  let s = seed % 2147483647 || 1
  while (out.length < count && arr.length) {
    s = (s * 48271) % 2147483647
    out.push(arr.splice(s % arr.length, 1)[0]!)
  }
  return out
}

/* ================= 干饭人格 ================= */

export type PersonalityKey = 'warrior' | 'chef' | 'carb' | 'health' | 'explorer' | 'solo'

export interface QuizOption {
  label: string
  emoji: string
  /** 默契比对的「大方向」分组：同组不同项算「大方向一致」 */
  group: string
  scores: Partial<Record<PersonalityKey, number>>
}

export interface QuizQuestion {
  emoji: string
  text: string
  options: QuizOption[]
}

export const QUIZ: QuizQuestion[] = [
  {
    emoji: '🍳',
    text: '周五晚上终于轮到你开火，你想整点什么？',
    options: [
      { label: '硬菜大餐', emoji: '🍖', group: '宴', scores: { explorer: 2, chef: 1 } },
      { label: '十分钟出锅', emoji: '⏱️', group: '快', scores: { warrior: 2, carb: 1 } },
      { label: '火锅一锅端', emoji: '🍲', group: '锅', scores: { chef: 2, carb: 1 } },
      { label: '外卖躺平', emoji: '🛵', group: '懒', scores: { carb: 2, warrior: 1 } },
    ],
  },
  {
    emoji: '🤔',
    text: '面对一道从没吃过的菜，你会？',
    options: [
      { label: '必须试', emoji: '🧭', group: '探', scores: { explorer: 3 } },
      { label: '看评价再说', emoji: '🔍', group: '稳', scores: { health: 1, chef: 1 } },
      { label: '只吃熟的', emoji: '🔁', group: '恋旧', scores: { solo: 2, health: 1 } },
      { label: '先拍照', emoji: '📸', group: '晒', scores: { carb: 1, explorer: 1 } },
    ],
  },
  {
    emoji: '🧊',
    text: '打开你的冰箱，里面最多的是？',
    options: [
      { label: '酱料比食材多', emoji: '🧂', group: '重口', scores: { chef: 2, explorer: 1 } },
      { label: '满满当当', emoji: '🛒', group: '囤', scores: { health: 2, chef: 1 } },
      { label: '几乎全空', emoji: '🌬️', group: '轻', scores: { health: 1, solo: 1 } },
      { label: '全是快乐水', emoji: '🥤', group: '快乐水', scores: { carb: 3 } },
    ],
  },
  {
    emoji: '😋',
    text: '吃到特别好吃的一口，你的第一反应？',
    options: [
      { label: '发出来馋大家', emoji: '📱', group: '晒', scores: { carb: 1, explorer: 1 } },
      { label: '默默记下来', emoji: '📝', group: '记', scores: { solo: 2, chef: 1 } },
      { label: '立刻安利朋友', emoji: '📣', group: '安利', scores: { explorer: 2, warrior: 1 } },
      { label: '别说话，炫饭', emoji: '🍚', group: '干', scores: { warrior: 3 } },
    ],
  },
  {
    emoji: '🌙',
    text: '深夜十一点，肚子叫了，你？',
    options: [
      { label: '煮碗面', emoji: '🍜', group: '快', scores: { chef: 2, solo: 1 } },
      { label: '喝水睡觉', emoji: '🥛', group: '自律', scores: { health: 3 } },
      { label: '炸鸡安排', emoji: '🍗', group: '放纵', scores: { carb: 3 } },
      { label: '翻出藏的零食', emoji: '🍪', group: '囤', scores: { solo: 2, carb: 1 } },
    ],
  },
]

export interface Personality {
  key: PersonalityKey
  emoji: string
  name: string
  tagline: string
  traits: string[]
  reading: string
  lucky: string
  /** 幸运菜对应的心情标签：接通菜谱库按心情筛选，让「幸运菜类型」有真实出口 */
  luckyMood: string
}

export const PERSONALITIES: Record<PersonalityKey, Personality> = {
  warrior: {
    key: 'warrior',
    emoji: '🍚',
    name: '干饭勇者',
    tagline: '干饭即正义',
    traits: ['饭点雷打不动', '炫饭不看表', '快乐很简单'],
    reading: '你吃饭从不扭捏——到点就干，好吃就炫。锅仔最省心的就是你这类人：不用猜你想吃什么，只需要问够不够吃。',
    lucky: '管饱硬菜',
    luckyMood: '满足',
  },
  chef: {
    key: 'chef',
    emoji: '🍲',
    name: '深夜食堂主理人',
    tagline: '灶台是你的主场',
    traits: ['啥都能往锅里放', '自带锅气', '深夜煮面大师'],
    reading: '你的字典里没有「不会做」，只有「还没试」。一锅炖万物，剩菜到你手里都能翻出新生命。锅仔想拜你为师，顺便蹭顿饭。',
    lucky: '一锅炖',
    luckyMood: '嘴馋',
  },
  carb: {
    key: 'carb',
    emoji: '🍜',
    name: '碳水快乐星人',
    tagline: '碳水才是本命',
    traits: ['没有主食不叫吃饭', '快乐水续命', '快乐很便宜'],
    reading: '米饭面条馒头包子，你对碳水的爱坦荡又坚定。锅仔不会劝你戒——只会把主食做得更香，让快乐加倍。',
    lucky: '碳水炸弹',
    luckyMood: '开心',
  },
  health: {
    key: 'health',
    emoji: '🥗',
    name: '养生佛系厨',
    tagline: '温和地吃好每一天',
    traits: ['作息比锅仔还规律', '蔬菜牛肉轮着来', '很少点外卖'],
    reading: '你不追风口，只追身体舒服。饭吃得清淡但讲究，锅仔的《膳食指南》弹药在你这里最派得上用场。',
    lucky: '清爽轻食',
    luckyMood: '平静',
  },
  explorer: {
    key: 'explorer',
    emoji: '🌶️',
    name: '味蕾探险家',
    tagline: '没吃过的都值得一试',
    traits: ['专点最陌生的那道', '酱料收藏家', '评价区常客'],
    reading: '别人点菜看价格，你点菜看眼缘。锅仔最喜欢给你推荐——因为你真会去做，还会回来告诉它好不好吃。',
    lucky: '猎奇新菜',
    luckyMood: '期待',
  },
  solo: {
    key: 'solo',
    emoji: '🥢',
    name: '一人食治愈家',
    tagline: '一个人也要好好吃饭',
    traits: ['碗筷一套刚好', '恋旧且专一', '好吃的记在小本本上'],
    reading: '你的餐桌不大，但每一顿都认真。一个人的饭最见功力——锅仔会记住你的口味，慢慢帮你攒一份专属菜单。',
    lucky: '一人食小锅',
    luckyMood: '想家',
  },
}

const KEY_ORDER: PersonalityKey[] = ['warrior', 'chef', 'carb', 'health', 'explorer', 'solo']

/** 诚实计分：结果完全由答案决定；平分时按 KEY_ORDER 取先到者 */
export function scorePersonality(answers: number[]): { key: PersonalityKey, scores: Record<PersonalityKey, number> } {
  const total: Record<PersonalityKey, number> = { warrior: 0, chef: 0, carb: 0, health: 0, explorer: 0, solo: 0 }
  answers.forEach((opt, qi) => {
    const scores = QUIZ[qi]?.options[opt]?.scores || {}
    for (const k of KEY_ORDER) total[k] += scores[k] || 0
  })
  let best: PersonalityKey = 'warrior'
  let bestV = -1
  for (const k of KEY_ORDER) {
    if (total[k] > bestV) {
      bestV = total[k]
      best = k
    }
  }
  return { key: best, scores: total }
}

/* 答案编码：5 道题各选 0-3，拼成 5 位数字串塞进分享链接，零后端传答案 */

export function encodeAnswers(answers: number[]): string {
  return answers.join('')
}

export function decodeAnswers(raw: string | undefined): number[] | null {
  if (!raw || !/^[0-3]{5}$/.test(raw))
    return null
  return raw.split('').map(Number)
}

/* ================= 默契度 ================= */

export interface CompatItem {
  q: string
  host: string
  guest: string
  level: 'exact' | 'same' | 'diff'
}

export interface CompatResult {
  score: number
  tier: { name: string, line: string }
  per: CompatItem[]
}

/** 默契计分：完全一致 20 分，大方向一致 12 分，各有所爱 4 分，满分 100 */
export function compatOf(host: number[], guest: number[]): CompatResult {
  const per: CompatItem[] = host.map((h, i) => {
    const ho = QUIZ[i]?.options[h]
    const go = QUIZ[i]?.options[guest[i]]
    const level: CompatItem['level'] = h === guest[i] ? 'exact' : ho && go && ho.group === go.group ? 'same' : 'diff'
    return {
      q: QUIZ[i]?.text || '',
      host: ho?.label || '',
      guest: go?.label || '',
      level,
    }
  })
  const score = per.reduce((sum, p) => sum + (p.level === 'exact' ? 20 : p.level === 'same' ? 12 : 4), 0)
  return { score, tier: compatTier(score), per }
}

function compatTier(score: number): { name: string, line: string } {
  if (score >= 90)
    return { name: '灵魂饭搭子', line: '一个锅都不用分——点菜从来不需要商量' }
  if (score >= 70)
    return { name: '桌上好搭子', line: '口味大同小异，剩下的分歧留给甜咸豆腐脑' }
  if (score >= 40)
    return { name: '一桌两派', line: '能坐一桌，但各点各的——刚好互相尝一口' }
  return { name: '各吃各的', line: '你吃你的山珍，我炫我的海味——互相尊重，饭照吃' }
}

export const COMPAT_LEVEL_TEXT: Record<CompatItem['level'], string> = {
  exact: '一模一样',
  same: '大方向一致',
  diff: '各有所爱',
}
