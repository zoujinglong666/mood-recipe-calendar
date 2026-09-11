import { get, post } from './request'

export interface PlanDay { day: string, dishName: string, ingredients: string[], steps: string[], reuseHint: string, healthTip: string, imageUrl?: string, fallbackImageUrl?: string }
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
export function generateWeeklyPlan(data: { people: number, days: number, healthGoal: string }) {
  return post<WeeklyPlan>('/weekly-plans/generate', data)
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
export function toggleShoppingItem(id: number, name: string) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/shopping/${encodeURIComponent(name)}`)
}
