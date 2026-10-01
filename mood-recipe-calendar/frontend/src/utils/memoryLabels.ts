/**
 * 锅仔记忆的「人话化」翻译层。
 *
 * 后端存储的是机器可读的事实（key=spiceLevel、value=TREAT、reason="…（置信度 0.95）：household=child"），
 * 直接把原始值展示给用户会造成「看得见但看不懂」。这里统一把 key / value / 来源 / 证据
 * 翻译成用户能理解的中文，页面上不再出现英文枚举与「key=value」这种调试串。
 */

export interface MemoryFact {
  key: string
  value: string
  category?: string
  confidence?: number
  source?: string
  evidence?: string
  reason?: string
}

/** 记忆 key → 用户能看懂的主题标签 */
const KEY_LABELS: Record<string, string> = {
  people: '用餐人数',
  dishesPerDay: '每餐几道菜',
  cookingDays: '一周做几天饭',
  spice: '辣度口味',
  spiceLevel: '辣度口味',
  household: '家里有谁一起吃饭',
  healthGoal: '健康目标',
  budget: '这一周的花销',
  favoriteCuisine: '偏爱的菜系',
  'dish.recent': '最近吃过的菜',
  'dish.avoid': '最近不想吃的菜',
  'strategy.skipQuestions': '锅仔不再重复问的事',
  'preference.maxCookingMinutes': '愿意花多久做饭',
  'preference.simpleDishes': '是否偏好简单菜',
  'safety.allergens': '过敏原（必避）',
  'safety.avoidIngredients': '忌口（要避开）',
  mealContext: '这顿饭的场景',
  feedback: '卡片互动情况',
}

/** value 枚举 → 中文（辣度 / 健康目标 / 预算 / 有无人） */
const VALUE_LABELS: Record<string, string> = {
  // 辣度
  NONE: '不吃辣',
  MILD: '微辣',
  NORMAL: '正常辣',
  HOT: '很能吃辣',
  // 健康目标
  BALANCED: '保持均衡',
  FITNESS: '健身增肌',
  LEAN: '轻盈减脂',
  // 预算
  SAVE: '想省着点花',
  TREAT: '想吃得丰盛些',
  // 有无家人
  elder: '有老人',
  child: '有小孩',
}

const CONSERVATIVE_WORDS = ['忌口', '过敏', '不吃', '不能吃', '避免', '避开', '安全']

/** 记忆 key → 主题标签文字（未知 key 兜底为去掉前缀的原 key，不再露出下划线） */
export function memoryKeyLabel(key?: string): string {
  const k = (key || '').trim()
  if (!k) return '一条记忆'
  if (KEY_LABELS[k]) return KEY_LABELS[k]
  if (k.startsWith('strategy.card.')) return '锅仔卡片的使用情况'
  if (k.startsWith('affinity.')) return `对「${k.slice('affinity.'.length)}」的偏好`
  if (k.startsWith('strategy.')) return '锅仔的对话策略'
  return k.replace(/^[a-z]+\./i, '')
}

/** 把单个原始值翻译成人话 */
function translateValue(raw: string): string {
  const v = raw.trim()
  if (VALUE_LABELS[v]) return VALUE_LABELS[v]
  if (v === 'true') return '是'
  if (v === 'false') return '否'
  return v
}

/**
 * 把机器可读的 value 翻译成用户能看懂的一句话。
 * 例：`[5,6]` → 「周六、周日」；`shown=1;answered=0;typed=0` → 「锅仔推荐了 1 次，你回应了 0 次」
 */
export function humanizeMemoryValue(key: string, value?: string): string {
  const v = (value || '').trim()
  if (!v) return ''

  const k = (key || '').trim()

  // 一周做几天饭：[5,6] / 5,6 → 周几
  if (k === 'cookingDays') {
    const days = v.replace(/[[\]]/g, '').split(/[,，、;；|\s]+/).map(Number).filter(n => !Number.isNaN(n))
    if (!days.length) return v
    return days.map(d => WEEKDAY_CN[d] || `${d + 1}`).join('、')
  }

  // 过长的说明性文本直接原样展示，不做 key=value 解析
  if (v.length > 60 || /[\u4E00-\u9FA5]/.test(v)) {
    // 但形如 xxx=yyy 的仍要拆开翻译
    if (v.includes('=') && !v.includes('；')) {
      return v.split('=').map(part => translateValue(part)).filter(Boolean).join('：')
    }
    return v
  }

  // 卡片互动统计：shown=1;answered=0;typed=0
  if (v.includes('shown') || v.includes('answered') || v.includes('typed')) {
    const pick = (name: string) => {
      const m = v.match(new RegExp(`${name}=(\\d+)`))
      return m ? m[1] : '0'
    }
    const parts: string[] = []
    if (v.includes('shown')) parts.push(`锅仔推荐 ${pick('shown')} 次`)
    if (v.includes('answered')) parts.push(`你回应 ${pick('answered')} 次`)
    if (v.includes('typed')) parts.push(`你主动说 ${pick('typed')} 次`)
    return parts.join('，') || v
  }

  if (v.includes('=')) {
    return v.split('=').map(part => translateValue(part)).filter(Boolean).join('：')
  }
  return translateValue(v)
}

/** 来源 → 用户能理解的「锅仔怎么知道的」 */
export function sourceLabel(fact: MemoryFact): string {
  const source = (fact.source || '').toUpperCase()
  if (source === 'EXPLICIT') return '你在偏好设置里选过'
  if (source === 'CHAT') return '你在聊天时提过'
  if (source === 'INFERRED') return '锅仔从你的话里猜的'
  if (source === 'BEHAVIOR') return '根据你的记录统计得出'
  if (source === 'LEARNED') return '根据你的反馈学会的'
  const reason = fact.reason || ''
  if (reason.includes('偏好页')) return '你在偏好设置里选过'
  if (reason.includes('对话')) return '你在聊天时提过'
  if (reason.includes('推断')) return '锅仔从你的话里猜的'
  if (reason.includes('行为') || reason.includes('记录')) return '根据你的记录统计得出'
  if (reason.includes('学习') || reason.includes('反馈')) return '根据你的反馈学会的'
  return '锅仔记下的一件事'
}

const WEEKDAY_CN = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

/** 判定是否安全/忌口类记忆（影响色调与文案） */
export function isSafetyMemory(key?: string): boolean {
  const k = (key || '').toLowerCase()
  return CONSERVATIVE_WORDS.some(w => k.includes(w))
}
