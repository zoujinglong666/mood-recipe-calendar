import { get } from './request'

/** 每周主题运营位：按周确定性轮换，预设带入周计划生成。 */
export interface WeeklyTheme {
  code: string
  emoji: string
  title: string
  subtitle: string
  healthGoal: string
  budget: string
  notes: string
  seasonalTip?: string | null
}

export function fetchWeeklyTheme() {
  return get<WeeklyTheme>('/weekly-theme')
}
