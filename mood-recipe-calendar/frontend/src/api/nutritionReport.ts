import { get } from './request'

export interface DishCount {
  name: string
  count: number
}

export interface MoodCount {
  tag: string
  count: number
}

export interface NutritionReport {
  month: string
  days: number
  meals: number
  distinctDishes: number
  avgCookingTime: number
  longestStreak: number
  topDishes: DishCount[]
  topMoods: MoodCount[]
  letterTitle: string
  letterParagraphs: string[]
}

/** 本月营养月报（会员专享，家庭共享成员可看自己的）。 */
export function fetchNutritionReport() {
  return get<NutritionReport>('/nutrition-report/current')
}
