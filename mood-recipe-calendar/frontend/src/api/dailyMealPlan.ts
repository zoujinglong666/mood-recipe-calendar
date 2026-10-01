import { get, post } from './request'
import type { RecipeItem } from './recipes'

export interface DailyMealPlan { date: string; meals: RecipeItem[]; knowledgePackVersion: string }
export function fetchDailyMealPlan(date?: string) { return get<DailyMealPlan>('/daily-meal-plan', { date }) }
export function replaceDailyMealPlanMeal(mealIndex: number, date?: string) { return post<DailyMealPlan>(`/daily-meal-plan/${mealIndex}/replace`, undefined, undefined, { date: date || '' }) }
