import { get } from './request'

export interface AlbumItem {
  id: number
  openid: string
  month: string
  recordIds: string
  coverText: string
  stats: string
  aiSummary: string
  generatedAt: string
}

/** 获取/生成月度画册 */
export function fetchMonthAlbum(openid: string, month: string) {
  return get<AlbumItem>('/albums/month', { month })
}
