import { post } from './request'

export interface PersonalityView {
  typeCode: string
  name: string
  emoji: string
  tagline: string
  /** AI 语义点评（引用了具体选择）；aiGenerated=false 时为确定性兜底文案 */
  reading: string
  luckyMood: string
  aiGenerated: boolean
}

/** 人格测试 · AI 语义判定：12 题答案 → 统一 16 型目录判型 + 锅仔专属点评（test: eat=干饭 / cook=做饭）。 */
export function analyzePersonality(answers: number[], test: 'eat' | 'cook' = 'eat') {
  return post<PersonalityView>('/personality/analyze', { answers, test }, 50000)
}
