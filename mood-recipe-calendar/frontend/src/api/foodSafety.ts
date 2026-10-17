import { get } from './request'

export type FoodTipType = 'PICK' | 'COOK' | 'AVOID' | 'COMBO'

export interface FoodTip {
  /** 触发提示的食材名 */
  name: string
  type: FoodTipType
  text: string
}

/**
 * 按食材名查询食品安全提示（服务端内置知识库，升级无需发版）。
 * names 逗号分隔；PICK=挑选技巧（采购清单用），COOK=烹调警示（菠菜焯水去草酸），
 * AVOID=慎食人群，COMBO=高风险搭配（柿子×螃蟹）。
 */
export function fetchFoodTips(names: string[]) {
  const q = names.map(n => n.trim()).filter(Boolean).slice(0, 40).join(',')
  if (!q) return Promise.resolve<FoodTip[]>([])
  return get<{ tips: FoodTip[] }>('/food-safety/tips', { names: q })
    .then(res => (Array.isArray(res?.tips) ? res.tips : []))
}
