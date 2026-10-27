import { get, post, resolveAssetUrl } from './request'

export interface RecipeItem {
  id?: number
  exposureId?: string
  name: string
  description: string
  image: string
  ingredients: string
  steps: string
  cookingTime: number
  difficulty: string
  moodTags: string
  season: string
  source: 'AI' | 'LOCAL'
  recommendationReason?: string
  recommendationInsights?: RecommendationInsight[]
  /** 推荐依据：本次推荐检索到的《中国居民膳食指南》要点（JSON 串），用于「有迹可循」展示 */
  guidelineBasis?: string
}

export interface RecommendationInsight {
  type: 'MEMORY' | 'CHANGE' | 'FRIDGE'
  text: string
  memoryKey?: string | null
}

export type RecommendationJobStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type RecommendationStepStatus = 'WAITING' | 'RUNNING' | 'COMPLETED' | 'DEGRADED' | 'FAILED'
export type RecommendationStage = 'MEMORY' | 'TEXT' | 'IMAGE' | 'LOCAL_FALLBACK' | 'FINALIZE'

export interface RecommendationStep {
  stage: RecommendationStage
  status: RecommendationStepStatus
  label: string
  message: string
}

export interface RecommendationJob {
  jobId: string
  status: RecommendationJobStatus
  currentStage?: RecommendationStage
  steps: RecommendationStep[]
  message: string
  usedFallback: boolean
  recipe?: RecipeItem
  /** 任务创建时的心情与餐别：恢复进行中任务前校验，避免换餐别后复用到旧任务（如选了早餐却拿到炒菜） */
  mood?: string
  mealType?: string
}

export interface UsageQuotaView {
  member: boolean
  limit: number
  used: number
  remaining: number
  resetsAt: string
}

export interface RecommendationHistoryItem {
  id: number
  name: string
  description: string
  image: string
  ingredients: string
  steps: string
  cookingTime?: number
  difficulty?: string
  source?: string
  recommendedAt: string
}

/** 菜谱库分页（懒加载）：筛选下推后端，返回 {list,total,page,size} */
export interface RecipeLibraryPage {
  list: RecipeItem[]
  total: number
  page: number
  size: number
}

export function fetchRecipeLibrary(params: {
  page?: number
  size?: number
  keyword?: string
  source?: string
  time?: string
  mood?: string
}) {
  return get<RecipeLibraryPage>('/recipes/library', params).then(res => ({
    ...res,
    list: (res.list ?? []).map(normalizeRecipe),
  }))
}

/** 菜谱库标签云：后端全表聚合的有效心情标签 */
export function fetchMoodTags() {
  return get<string[]>('/recipes/mood-tags').then(tags => (Array.isArray(tags) ? tags : []))
}

/** 按心情优先获取 AI 生成的菜谱；服务端不可用时自动回退到菜谱库 */
export function recommendRecipe(mood: string) {
  return get<RecipeItem>('/recipes/recommend', { mood }).then(normalizeRecipe)
}

/** 创建可查询真实执行阶段的今日推荐任务。mealType 可选：BREAKFAST / LUNCH / DINNER；maxMinutes 为本次时间预算（分钟），约束推荐打分与生成。 */
export function createRecommendationJob(mood: string, mealType?: string, maxMinutes?: number) {
  return post<RecommendationJob>('/recipes/recommend-jobs', { mood, mealType, maxMinutes }).then(job => ({
    ...job,
    recipe: job.recipe ? normalizeRecipe(job.recipe) : undefined,
  }))
}

export function fetchRecipeQuota() {
  return get<UsageQuotaView>('/recipes/quota')
}

/** 全库 AI 菜谱池（source=AI 且有真实图片，即所有用户的 AI 推荐记录），供锅仔转盘抽样。 */
export function fetchRecipePool() {
  return get<RecipeItem[]>('/recipes').then(items => (Array.isArray(items) ? items : []).map(normalizeRecipe))
}

export function fetchRecommendationHistory() {
  return get<RecommendationHistoryItem[]>('/recipes/history').then(items => items.map(item => ({ ...item, image: resolveAssetUrl(item.image) })))
}

/** 查询当前登录用户自己的推荐任务。 */
export function fetchRecommendationJob(jobId: string) {
  return get<RecommendationJob>(`/recipes/recommend-jobs/${jobId}`).then(job => ({
    ...job,
    recipe: job.recipe ? normalizeRecipe(job.recipe) : undefined,
  }))
}

export type RecipeFeedbackAction = 'LIKE' | 'DISLIKE' | 'MADE'

export interface RecipeFeedbackState {
  liked: boolean
  disliked: boolean
  made: boolean
}

/** 只上传用户对菜谱的行为，用于下一次推荐排序。 */
export function sendRecipeFeedback(recipe: Pick<RecipeItem, 'id' | 'exposureId'>, action: RecipeFeedbackAction) {
  const path = recipe.exposureId
    ? `/recipes/exposures/${encodeURIComponent(recipe.exposureId)}/feedback`
    : `/recipes/${recipe.id}/feedback`
  return post<RecipeFeedbackState>(path, { action })
}

export function fetchRecipeFeedback(recipe: Pick<RecipeItem, 'id' | 'exposureId'>) {
  const path = recipe.exposureId
    ? `/recipes/exposures/${encodeURIComponent(recipe.exposureId)}/feedback`
    : `/recipes/${recipe.id}/feedback`
  return get<RecipeFeedbackState>(path)
}

/** 已解锁 AI 私人菜单后，按食材、时长与口味生成菜谱。 */
export function requestDeepRecipe(payload: {
  openid: string
  mood: string
  ingredients?: string
  maxMinutes?: string
  preference?: string
}) {
  const { openid: _openid, ...request } = payload
  return post<RecipeItem>('/recipes/deep-recommend', request).then(normalizeRecipe)
}

/** 按心情列表 */
export function fetchRecipesByMood(mood: string) {
  return get<RecipeItem[]>('/recipes/by-mood', { mood }).then(items => items.map(normalizeRecipe))
}

/** 菜谱教程海报（服务端模板排版 + 封面图，免费）：返回可保存/分享的图片 URL */
export function fetchRecipePoster(id: number) {
  return post<{ url: string }>(`/recipes/${id}/poster`, undefined, 30000).then(res => res?.url)
}

/** 菜谱详情 */
export function fetchRecipeDetail(id: number) {
  return get<RecipeItem>(`/recipes/${id}`).then(normalizeRecipe)
}

function normalizeRecipe(recipe: RecipeItem): RecipeItem {
  return { ...recipe, source: recipe.source === 'AI' ? 'AI' : 'LOCAL', image: resolveAssetUrl(recipe.image) }
}
