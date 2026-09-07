import { del, get, put } from './request'

export type SpiceLevel = 'NONE' | 'MILD' | 'NORMAL' | 'HOT'

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
  onboardingCompleted: boolean
  updatedAt?: string
}

export function fetchFoodPreference() {
  return get<FoodPreference>('/preferences')
}

export function saveFoodPreference(data: Omit<FoodPreference, 'id' | 'onboardingCompleted' | 'updatedAt'>) {
  return put<FoodPreference>('/preferences', data)
}

export function clearFoodPreference() {
  return del<void>('/preferences')
}
