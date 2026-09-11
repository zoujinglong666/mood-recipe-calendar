import { get, post } from './request'

export interface PlanDay { day: string, dishName: string, ingredients: string[], steps: string[], reuseHint: string, healthTip: string }
export interface ShoppingItem { name: string, category: string, quantity: string, purchased: boolean }
export interface WeeklyPlan { id: number, days: PlanDay[], shopping: ShoppingItem[] }

export function getCurrentPlan() {
  return get<WeeklyPlan>('/weekly-plans/current')
}
export function generateWeeklyPlan(data: { people: number, days: number, healthGoal: string }) {
  return post<WeeklyPlan>('/weekly-plans/generate', data)
}
export function replacePlanDay(id: number, index: number) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/days/${index}/replace`)
}
export function toggleShoppingItem(id: number, name: string) {
  return post<WeeklyPlan>(`/weekly-plans/${id}/shopping/${encodeURIComponent(name)}`)
}
