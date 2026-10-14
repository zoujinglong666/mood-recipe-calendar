import { get, put, del, post } from './request'

export interface AdminRecipe {
  id: number
  name: string
  description: string
  image: string
  ingredients: string
  steps: string
  cookingTime: number
  difficulty: string
  moodTags: string
  taste: string
  mealType: string
  tags: string
  seasonings: string
  season: string
  source: string
  createdAt: string
}

export interface AdminRecipeList {
  list: AdminRecipe[]
  total: number
  placeholderCount: number
  page: number
  size: number
}

export interface AdminStats {
  total: number
  placeholderImage: number
  ai: number
  local: number
}

export function adminRecipeStats() {
  return get<AdminStats>('/admin/recipes/stats')
}

/** 当前用户是否为管理员（非管理员返回 admin=false，不报错） */
export function adminCheck() {
  return get<{ admin: boolean }>('/admin/recipes/check')
}

export function adminRecipeList(params: {
  page?: number
  size?: number
  keyword?: string
  source?: string
  placeholderImage?: boolean
}) {
  return get<AdminRecipeList>('/admin/recipes', params)
}

export function adminRecipeDetail(id: number) {
  return get<AdminRecipe>(`/admin/recipes/${id}`)
}

export function adminRecipeUpdate(id: number, data: Partial<AdminRecipe>) {
  return put<AdminRecipe>(`/admin/recipes/${id}`, data)
}

export function adminRecipeDelete(id: number) {
  return del<{ deleted: number }>(`/admin/recipes/${id}`)
}

/** 为无图/占位图菜谱异步生成 AI 配图（文生图→COS→回写）。started=false 表示已有真实图，直接返回 image。 */
export function adminRecipeGenImage(id: number) {
  return post<{ started: boolean; image?: string }>(`/admin/recipes/${id}/image`, {})
}

/** 批量补拍缺图菜谱配图：按缺图口径取前 count 道（已有真实图自动跳过），逐道异步补拍，立即返回已排队条数。 */
export function adminRecipeBatchImage(count: number) {
  return post<{ queued: number; scanned: number }>('/admin/recipes/batch-image', { count })
}

export interface GenerationConfig {
  enabled: boolean
  dailyCount: number
  lastRunAt: string | null
  lastRunCount: number
  lastRunMessage: string | null
  cron: string
}

export function adminGetGenerationConfig() {
  return get<GenerationConfig>('/admin/recipes/generation-config')
}

export function adminUpdateGenerationConfig(data: { enabled?: boolean; dailyCount?: number }) {
  return put<{ enabled: boolean; dailyCount: number }>('/admin/recipes/generation-config', data)
}

export function adminRunGenerationNow(count?: number) {
  return post<{ saved: number; message: string }>('/admin/recipes/generation-run', { count })
}
