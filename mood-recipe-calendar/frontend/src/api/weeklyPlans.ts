import { get, post } from './request'

export interface PlanDish { name: string, ingredients: string[], steps: string[], fallbackImageUrl?: string, imageUrl?: string }
export interface PlanDay { day: string, dishName: string, ingredients: string[], steps: string[], reuseHint: string, healthTip: string, imageUrl?: string, fallbackImageUrl?: string, dishes?: PlanDish[] }
export interface PlanAudit { score?: number | null, degradeReasons: string[], memoryUsed: string[], issues: string[], traceId?: string | null }
export interface ShoppingItem { name: string, category: string, quantity: string, purchased: boolean }
export interface WeeklyPlan { id: number, days: PlanDay[], shopping: ShoppingItem[], favorite: boolean, createdAt: string, agent?: PlanAudit | null }
export interface PlanOutcomeAck { cuisineAffinity: Record<string, number>, skipQuestions: string[], maxCookingMinutes: number | null, preferSimple: boolean, avoidDishes: string[] }
export interface WeeklyPlanSummary { id: number, createdAt: string, favorite: boolean, days: PlanDay[] }
export interface MealAgentState { people?: number, cookingDays?: number[], dishesPerDay?: number, healthGoal?: string, budget?: string, hasElder?: boolean, hasChild?: boolean, spiceLevel?: string, favoriteCuisine?: string, cuisineConfirmed?: boolean, mealContext?: string }
export interface MealAgentOption { label: string, value: string }
export interface MealAgentTurn {
  reply: string
  action: string
  state: MealAgentState
  card: { type: string, title: string, description: string, options: MealAgentOption[] }
  askReason?: string
  memoryUsed?: string[]
  conflicts?: string[]
  degraded?: string[]
}

export function getCurrentPlan() {
  return get<WeeklyPlan>('/weekly-plans/current')
}
export function getWeeklyPlan(id: number) {
  return get<WeeklyPlan>(`/weekly-plans/${id}`)
}
export function getWeeklyPlanHistory() {
  return get<WeeklyPlanSummary[]>('/weekly-plans/history')
}
export function generateWeeklyPlan(data: { people: number, days: number, cookingDays: number[], healthGoal: string, sendNotification: boolean, dishesPerDay: number, budget?: string, conversationNotes?: string }) {
  return post<WeeklyPlan>('/weekly-plans/generate', data)
}
export function askMealAgent(message: string, nextQuestion: string) {
  return post<{ reply: string }>('/weekly-plans/agent-replies', { message, nextQuestion })
}
export function runMealAgentTurn(message: string, state: MealAgentState) {
  return post<MealAgentTurn>('/weekly-plans/agent-turns', { message, state })
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
/** 把"做没做成、剩没剩、难不难"反馈给智能体，它才会越用越准。 */
export function reportPlanDishOutcome(data: { planId: number, dayIndex: number, dishIndex: number, dishName: string, cooked?: boolean, leftover?: boolean, tooHard?: boolean }) {
  return post<PlanOutcomeAck>('/agent/outcomes', data)
}
export function toggleShoppingItem(id: number, name: string) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/shopping/${encodeURIComponent(name)}`)
}
