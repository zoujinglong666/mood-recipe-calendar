import { post } from './request'

export interface SeasoningTip {
  /** 调料名（如：盐、料酒） */
  name: string
  /** 什么时候放（如：出锅前 30 秒再放） */
  timing: string
  /** 为什么（原理） */
  reason: string
}

/**
 * 调味时序提示：服务端内置知识库按食材与步骤文本匹配「什么时候放调料」。
 * ingredients/steps 任传或都传，命中越多提示越全。
 */
export function fetchSeasoningTips(ingredients: string[], steps: string[]) {
  return post<{ tips: SeasoningTip[] }>('/seasoning-tips', {
    ingredients: (ingredients || []).slice(0, 40),
    steps: (steps || []).slice(0, 30),
  }, 15000).then(res => (Array.isArray(res?.tips) ? res.tips : []))
}
