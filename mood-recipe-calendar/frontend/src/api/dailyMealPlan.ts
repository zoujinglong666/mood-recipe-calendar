import { get, post, resolveAssetUrl } from './request'
import type { RecipeItem } from './recipes'

export interface GuidelinePoint {
  title: string
  content: string
  source?: string
}

export interface NutritionSnapshot {
  headline: string
  tags: string[]
  tip: string
}

export interface DailyMealPlan {
  date: string
  meals: RecipeItem[]
  knowledgePackVersion: string
  guidelineBasis?: GuidelinePoint[]
  nutrition?: NutritionSnapshot
}

export function fetchDailyMealPlan(date?: string) {
  return get<DailyMealPlan>('/daily-meal-plan', { date }).then(normalizePlan)
}

export function replaceDailyMealPlanMeal(mealIndex: number, date?: string) {
  return post<DailyMealPlan>(`/daily-meal-plan/${mealIndex}/replace`, undefined, undefined, { date: date || '' })
    .then(normalizePlan)
}

function normalizePlan(plan: DailyMealPlan): DailyMealPlan {
  if (!plan) return plan
  return {
    ...plan,
    meals: (plan.meals || []).map((m) => ({ ...m, image: resolveAssetUrl(m.image) })),
  }
}
