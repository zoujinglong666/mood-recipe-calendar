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
