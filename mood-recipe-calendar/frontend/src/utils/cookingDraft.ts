import type { RecipeItem } from '@/api/recipes'

export const COOKING_DRAFT_KEY = 'mrc_cooking_draft'
export const COOKING_PROGRESS_KEY = 'mrc_cooking_progress'
export const RECORD_DRAFT_KEY = 'mrc_record_draft'

export interface CookingDraft {
  recipe: RecipeItem
  mood: string
  createdAt: number
}

export function saveCookingDraft(recipe: RecipeItem, mood: string) {
  uni.setStorageSync(COOKING_DRAFT_KEY, { recipe, mood, createdAt: Date.now() } satisfies CookingDraft)
}

export function loadCookingDraft(): CookingDraft | null {
  const value = uni.getStorageSync(COOKING_DRAFT_KEY)
  return value?.recipe ? value as CookingDraft : null
}

export function createRequestId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

export function saveRecordDraft(recipe: RecipeItem, mood: string) {
  uni.setStorageSync(RECORD_DRAFT_KEY, {
    dish: recipe.name,
    mood,
    recipeId: recipe.id,
    exposureId: recipe.exposureId,
    image: recipe.image,
    cookingTime: recipe.cookingTime,
    clientRequestId: createRequestId(),
    source: 'recipe',
  })
}
