import { get, post } from './request'

export interface NutritionGroupAdvice {
  name: string
  advice: string
}

export interface NutritionPyramidLayer {
  key: string
  value: string
  advice: string
  group: string
}

export interface NutritionAdvice {
  /** 整体结论，如「高血糖」 */
  headline: string
  headlineAdvice: string
  keyNutrient: string
  keyValue: string
  groups: NutritionGroupAdvice[]
  pyramid: NutritionPyramidLayer[]
  bullets: string[]
}

/** 按菜谱 id 取营养建议；404 表示估算暂不可用，前端不渲染卡片 */
export function fetchRecipeNutrition(id: number) {
  return get<{ advice: NutritionAdvice, estimated: boolean }>(`/nutrition/recipe/${id}`)
    .then(res => res?.advice)
    .catch(() => undefined)
}

/** 按菜名+食材串估算（今日三餐每餐用），ingredients 兼容 JSON 数组与顿号分隔文本 */
export function analyzeNutrition(name: string, ingredients: string) {
  return post<{ advice: NutritionAdvice, estimated: boolean }>('/nutrition/analyze', { name, ingredients })
    .then(res => res?.advice)
    .catch(() => undefined)
}

// ===== [2026-10-28] AI 营养师：逐餐营养方案与整日对照 =====

/**
 * status: OK / NEEDS_INPUT（身体数据不全，引导补问卷）/ NO_PLAN（今日三餐未生成）。
 * source: LLM（模型解读）/ RULE（规则估算）；宏量数字均为估算值，展示需带「约」。
 */
export interface CoachMealAnalysis {
  index: number
  name: string
  portion: string
  kcal?: number | null
  proteinG?: number | null
  carbsG?: number | null
  fatG?: number | null
  reason: string
  goalTip: string
  estimated: boolean
}

export interface CoachDailyAnalysis {
  totalKcal?: number | null
  targetKcal?: number | null
  balance: string
  gapTip: string
}

export interface NutritionCoachView {
  status: 'OK' | 'NEEDS_INPUT' | 'NO_PLAN'
  message: string
  source: 'LLM' | 'RULE' | 'NONE'
  meals: CoachMealAnalysis[]
  daily: CoachDailyAnalysis | null
}

export function fetchNutritionCoach() {
  return get<NutritionCoachView>('/nutrition/coach')
}
