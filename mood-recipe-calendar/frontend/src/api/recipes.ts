import { get } from './request'

export interface RecipeItem {
  id: number
  name: string
  description: string
  image: string
  ingredients: string
  steps: string
  cookingTime: number
  difficulty: string
  moodTags: string
  season: string
}

/** 全部菜谱 */
export function fetchAllRecipes() {
  return get<RecipeItem[]>('/recipes')
}

/** 按心情随机推荐一道 */
export function recommendRecipe(mood: string) {
  return get<RecipeItem>('/recipes/recommend', { mood })
}

/** 按心情列表 */
export function fetchRecipesByMood(mood: string) {
  return get<RecipeItem[]>('/recipes/by-mood', { mood })
}

/** 菜谱详情 */
export function fetchRecipeDetail(id: number) {
  return get<RecipeItem>(`/recipes/${id}`)
}
