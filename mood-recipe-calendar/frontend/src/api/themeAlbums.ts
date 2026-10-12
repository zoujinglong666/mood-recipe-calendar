import { get } from './request'

export interface ThemeAlbumDish {
  id: number
  name: string
  image: string | null
  cookingTime: number | null
  difficulty: string | null
  description: string | null
  /** 未购买时为空 */
  ingredients: string | null
  /** 未购买时为空 */
  steps: string | null
}

export interface ThemeAlbumItem {
  sku: string
  title: string
  subtitle: string
  cover: string
  description: string
  dishCount: number
  owned: boolean
  priceFen: number | null
}

export interface ThemeAlbumDetail {
  sku: string
  title: string
  subtitle: string
  cover: string
  description: string
  owned: boolean
  visibleCount: number
  totalCount: number
  dishes: ThemeAlbumDish[]
  priceFen: number | null
}

export function fetchThemeAlbums() {
  return get<ThemeAlbumItem[]>('/theme-albums')
}

export function fetchThemeAlbumDetail(sku: string) {
  return get<ThemeAlbumDetail>(`/theme-albums/${encodeURIComponent(sku)}`)
}
