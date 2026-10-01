import { get, post, resolveAssetUrl } from './request'
import type { RecipeItem } from './recipes'

/** 今日菜单（TodayBoard）：打开即有的零输入每日答案，服务端当日缓存秒回。 */
export interface DailyBoard {
  date: string
  recipe: RecipeItem
  guozaiLine: string
  variant: number
  source: string
}

function normalizeBoard(board: DailyBoard): DailyBoard {
  const visibleText = (value: unknown) => typeof value !== 'string' || !/[?？�]/.test(value)
  if (!board?.recipe || !visibleText(board.recipe.name) || !visibleText(board.recipe.description)
    || !visibleText(board.recipe.ingredients) || !visibleText(board.recipe.steps) || !visibleText(board.guozaiLine)) {
    throw new Error('今日菜单数据异常，已拦截展示')
  }
  return { ...board, recipe: { ...board.recipe, image: resolveAssetUrl(board.recipe?.image || '') } }
}

/** 今日菜单：当日已生成则直接命中缓存；首次进入由服务端挑选并落缓存。 */
export function fetchDailyBoard() {
  return get<DailyBoard>('/daily-menu').then(normalizeBoard)
}

/** 换一道：排除当前这道重新挑选；没有别的可换时服务端返回 404。 */
export function refreshDailyBoard() {
  return post<DailyBoard>('/daily-menu/refresh', {}).then(normalizeBoard)
}
