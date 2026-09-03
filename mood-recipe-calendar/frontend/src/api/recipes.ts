import { get, post } from './request'

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

/** 按心情优先获取 AI 生成的菜谱；服务端不可用时自动回退到菜谱库 */
export function recommendRecipe(mood: string) {
  return get<RecipeItem>('/recipes/recommend', { mood })
}

/** 已解锁 AI 私人菜单后，按食材、时长与口味生成菜谱。 */
export function requestDeepRecipe(payload: {
  openid: string
  mood: string
  ingredients?: string
  maxMinutes?: string
  preference?: string
}) {
  return post<RecipeItem>('/recipes/deep-recommend', payload)
}

/** 按心情列表 */
export function fetchRecipesByMood(mood: string) {
  return get<RecipeItem[]>('/recipes/by-mood', { mood })
}

/** 菜谱详情 */
export function fetchRecipeDetail(id: number) {
  return get<RecipeItem>(`/recipes/${id}`)
}
