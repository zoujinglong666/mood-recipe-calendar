import { del, get, post, put } from './request'

export type SpiceLevel = 'NONE' | 'MILD' | 'NORMAL' | 'HOT'
export type HealthGoal = 'BALANCED' | 'FITNESS' | 'LEAN'
export type CookingSkill = 'NOVICE' | 'HOME' | 'PRO'

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
  hometown?: string
  cookingSkill?: CookingSkill
  /** [2026-10-28] 身体数据（EER 目标计算输入） */
  gender?: 'MALE' | 'FEMALE'
  age?: number
  heightCm?: number
  weightKg?: number
  activityLevel?: 'INACTIVE' | 'LOW_ACTIVE' | 'ACTIVE' | 'VERY_ACTIVE'
  resistanceTraining?: boolean | null
  mealTiming?: 'REGULAR' | 'NO_BREAKFAST' | 'LATE_NIGHT' | 'INTERMITTENT'
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

/** [2026-10-27] 冷启动画像问卷答案：全部字段可选（允许只答部分题）。 */
export interface OnboardingAnswers {
  /** 平时做一顿饭想花多久（分钟）：写入 KEY_MAX_MINUTES + 偏好打分 */
  maxMinutes?: number
  /** 辣度：写入 spiceLevel + KEY_SPICE */
  spiceLevel?: SpiceLevel
  /** 喜欢的菜系（≤5 个）：写入 favoriteCuisines + KEY_CUISINE */
  cuisines?: string[]
  /** 不吃的食材：写入 avoidIngredients + 安全键 safety.avoidIngredients */
  avoidIngredients?: string[]
  /** 过敏原：写入 allergens + 安全键 safety.allergens（硬过滤） */
  allergens?: string[]
  /** 家里几口人：写入 KEY_PEOPLE（为周计划人数缩放埋点） */
  people?: number
  /** [2026-10-28] 生理性别：MALE/FEMALE（EER 方程系数组用） */
  gender?: 'MALE' | 'FEMALE'
  /** 年龄（岁），EER 支持 19-64 */
  age?: number
  /** 身高 cm */
  heightCm?: number
  /** 体重 kg */
  weightKg?: number
  /** 活动水平 */
  activityLevel?: 'INACTIVE' | 'LOW_ACTIVE' | 'ACTIVE' | 'VERY_ACTIVE'
  /** 是否规律力量训练（决定蛋白质范围 1.4-2.0 vs 1.2-1.6 g/kg） */
  resistanceTraining?: boolean
  /** 进食节奏 */
  mealTiming?: 'REGULAR' | 'NO_BREAKFAST' | 'LATE_NIGHT' | 'INTERMITTENT'
}

/** 提交冷启动问卷：后端双轨写入显式偏好（onboardingCompleted=true）+ 记忆库（EXPLICIT/0.9）。 */
export function saveOnboarding(answers: OnboardingAnswers) {
  return post<FoodPreference>('/preferences/onboarding', answers)
}

/** [2026-10-28] 营养目标（NASEM 2023 EER）：状态 OK/NEEDS_INPUT/NOT_ELIGIBLE。 */
export interface NutritionRange { min: number, target: number, max: number }

export interface NutritionTarget {
  status: 'OK' | 'NEEDS_INPUT' | 'NOT_ELIGIBLE'
  missingFields?: string[]
  issues?: string[]
  bmi?: number
  maintenanceKcal?: number
  energyKcal?: NutritionRange
  proteinG?: NutritionRange
  energyOffsetPercent?: string
}

/** 获取个体营养目标：数据不足返回 NEEDS_INPUT（不猜测），不合适则 NOT_ELIGIBLE（issues 为机器码）。 */
export function fetchNutritionTarget() {
  return get<NutritionTarget>('/preferences/nutrition-target')
}
