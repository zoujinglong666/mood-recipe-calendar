import { del, get, put } from './request'

export type SpiceLevel = 'NONE' | 'MILD' | 'NORMAL' | 'HOT'
export type HealthGoal = 'BALANCED' | 'FITNESS' | 'LEAN'

export interface FoodPreference {
  id?: number
  favoriteTags: string
  favoriteCuisines?: string
  favoriteDishes: string
  avoidIngredients: string
  allergens: string
  eatScallion: boolean | null
  eatCilantro: boolean | null
  spiceLevel: SpiceLevel
  healthGoal?: HealthGoal
  onboardingCompleted: boolean
  updatedAt?: string
}

export interface FoodMemoryBehavior {
  topDish: string
  topMood: string
  streak: number
  recordedToday: boolean
  likedCount: number
  dislikedCount: number
  madeCount: number
}

export interface FoodMemoryView {
  explicit: FoodPreference
  behavior: FoodMemoryBehavior
}

export function fetchFoodPreference() {
  return get<FoodPreference>('/preferences')
}

export function fetchFoodMemory() {
  return get<FoodMemoryView>('/preferences/summary')
}

export function saveFoodPreference(data: Omit<FoodPreference, 'id' | 'onboardingCompleted' | 'updatedAt'>) {
  return put<FoodPreference>('/preferences', data)
}

export function clearFoodPreference() {
  return del<void>('/preferences')
}
