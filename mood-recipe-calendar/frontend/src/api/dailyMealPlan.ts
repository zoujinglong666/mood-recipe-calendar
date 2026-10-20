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

/** 换一整天额度视图：remaining=-1 表示无限（会员上限或看广告解锁）。 */
export interface QuotaView {
  member: boolean
  limit: number
  used: number
  remaining: number
  resetsAt: string
}

/** 查询「换一整天」剩余额度（普通 1 / 会员 3 / 看广告无限）。 */
export function fetchDailyMealPlanQuota() {
  return get<QuotaView>('/daily-meal-plan/quota')
}

/** 换一整天：后端整包重选三餐并扣一次额度，返回新计划与最新额度。 */
export function reshuffleDailyMealPlan(date?: string) {
  return post<{ plan: DailyMealPlan; quota: QuotaView }>('/daily-meal-plan/reshuffle', undefined, undefined, { date: date || '' })
    .then(res => ({
      plan: normalizePlan(res.plan),
      quota: res.quota,
    }))
}

export function replaceDailyMealPlanMeal(mealIndex: number, date?: string) {
  return post<DailyMealPlan>(`/daily-meal-plan/${mealIndex}/replace`, undefined, undefined, { date: date || '' })
    .then(normalizePlan)
}

/** 三餐卡封面懒生成：该餐没有真实图时由服务端补拍专属封面并回写（文生图较慢，前端应后台调用）。 */
export function generateDailyMealCover(mealIndex: number, date?: string) {
  // 文生图 30-90s + COS 转存，默认 60s 超时会掐断在途请求（服务端仍会落库），放宽到 150s
  return post<DailyMealPlan>(`/daily-meal-plan/${mealIndex}/cover`, undefined, 150000, { date: date || '' })
    .then(normalizePlan)
}

function normalizePlan(plan: DailyMealPlan): DailyMealPlan {
  if (!plan) return plan
  return {
    ...plan,
    meals: (plan.meals || []).map((m) => ({ ...m, image: resolveAssetUrl(m.image) })),
  }
}
