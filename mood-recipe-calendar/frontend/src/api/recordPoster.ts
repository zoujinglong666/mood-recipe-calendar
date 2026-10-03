import { post } from './request'

export interface RecordPosterResult {
  url: string
  mode: 'SERVER_POSTER'
}

/** 会员专享：由后端生成可保存/分享的杂志海报。 */
export function generateRecordPoster(id: number) {
  return post<RecordPosterResult>(`/records/${id}/poster`, undefined, 60000)
}
