import { get, post } from './request'

export interface PlanDish { name: string, ingredients: string[], steps: string[], fallbackImageUrl?: string, imageUrl?: string }
export interface PlanDay { day: string, dishName: string, ingredients: string[], steps: string[], reuseHint: string, healthTip: string, imageUrl?: string, fallbackImageUrl?: string, dishes?: PlanDish[] }
export interface ShoppingItem { name: string, category: string, quantity: string, purchased: boolean }
export interface WeeklyPlan { id: number, days: PlanDay[], shopping: ShoppingItem[], favorite: boolean, createdAt: string }
export interface WeeklyPlanSummary { id: number, createdAt: string, favorite: boolean, days: PlanDay[] }

export function getCurrentPlan() {
  return get<WeeklyPlan>('/weekly-plans/current')
}
export function getWeeklyPlan(id: number) {
  return get<WeeklyPlan>(`/weekly-plans/${id}`)
}
export function getWeeklyPlanHistory() {
  return get<WeeklyPlanSummary[]>('/weekly-plans/history')
}
export function generateWeeklyPlan(data: { people: number, days: number, cookingDays: number[], healthGoal: string, sendNotification: boolean, dishesPerDay: number }) {
  return post<WeeklyPlan>('/weekly-plans/generate', data)
}

const WEEKLY_PLAN_TEMPLATE_ID = 'h00FlM2Xf_X64sXln5WoYGnbvtJBjasdEraRPjs4NOg'

/** 只在用户点击生成按钮时调用；非微信端和拒绝授权均不影响生成计划。 */
export function requestWeeklyPlanCompletionNotice(): Promise<boolean> {
  return new Promise((resolve) => {
    // #ifdef MP-WEIXIN
    const requestSubscribeMessage = (uni as any).requestSubscribeMessage
    if (typeof requestSubscribeMessage !== 'function') {
      resolve(false)
      return
    }
    requestSubscribeMessage({
      tmplIds: [WEEKLY_PLAN_TEMPLATE_ID],
      success: (result: Record<string, string>) => resolve(['accept', 'acceptWithAudio'].includes(result[WEEKLY_PLAN_TEMPLATE_ID])),
      fail: () => resolve(false),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve(false)
    // #endif
  })
}
export function replacePlanDay(id: number, index: number) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/days/${index}/replace`)
}
export function toggleWeeklyPlanFavorite(id: number) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/favorite`)
}
export function generatePlanDayCover(id: number, index: number) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/days/${index}/cover`)
}
export function generatePlanDishCover(id: number, dayIndex: number, dishIndex: number) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/days/${dayIndex}/cover/${dishIndex}`)
}
export function toggleShoppingItem(id: number, name: string) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/shopping/${encodeURIComponent(name)}`)
}
