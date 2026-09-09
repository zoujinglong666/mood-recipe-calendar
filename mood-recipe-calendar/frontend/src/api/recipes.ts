import { get, post } from './request'

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
  recommendationReason?: string
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
}

/** 全部菜谱 */
export function fetchAllRecipes() {
  return get<RecipeItem[]>('/recipes')
}

/** 按心情优先获取 AI 生成的菜谱；服务端不可用时自动回退到菜谱库 */
export function recommendRecipe(mood: string) {
  return get<RecipeItem>('/recipes/recommend', { mood })
}

/** 创建可查询真实执行阶段的今日推荐任务。 */
export function createRecommendationJob(mood: string) {
  return post<RecommendationJob>('/recipes/recommend-jobs', { mood })
}

/** 查询当前登录用户自己的推荐任务。 */
export function fetchRecommendationJob(jobId: string) {
  return get<RecommendationJob>(`/recipes/recommend-jobs/${jobId}`)
}

export type RecipeFeedbackAction = 'LIKE' | 'DISLIKE' | 'MADE'

/** 只上传用户对菜谱的行为，用于下一次推荐排序。 */
export function sendRecipeFeedback(recipe: Pick<RecipeItem, 'id' | 'exposureId'>, action: RecipeFeedbackAction) {
  const path = recipe.exposureId
    ? `/recipes/exposures/${encodeURIComponent(recipe.exposureId)}/feedback`
    : `/recipes/${recipe.id}/feedback`
  return post<void>(path, { action })
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
  return post<RecipeItem>('/recipes/deep-recommend', request)
}

/** 按心情列表 */
export function fetchRecipesByMood(mood: string) {
  return get<RecipeItem[]>('/recipes/by-mood', { mood })
}

/** 菜谱详情 */
export function fetchRecipeDetail(id: number) {
  return get<RecipeItem>(`/recipes/${id}`)
}
