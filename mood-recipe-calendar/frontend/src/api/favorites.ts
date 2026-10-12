import { get, post, del, resolveAssetUrl } from './request'

/** 想吃清单条目：后端返回的菜谱摘要（不含食材/做法大字段）。 */
export interface FavoriteItem {
  recipeId: number
  name: string
  image: string | null
  cookingTime: number | null
  difficulty: string | null
  description: string | null
  source: string | null
}

export function fetchFavorites() {
  return get<FavoriteItem[]>('/recipes/favorites').then(items =>
    (Array.isArray(items) ? items : []).map(item => ({ ...item, image: item.image ? resolveAssetUrl(item.image) : null })),
  )
}

/** 收藏；幂等（重复收藏不报错）。 */
export function addFavorite(recipeId: string | number) {
  return post(`/recipes/favorites/${encodeURIComponent(recipeId)}`)
}

/** 取消收藏。 */
export function removeFavorite(recipeId: string | number) {
  return del(`/recipes/favorites/${encodeURIComponent(recipeId)}`)
}
