import { post } from './request'

/**
 * 激励视频广告位 id：在微信公众平台「流量主 → 激励视频广告」创建后填入。
 * 注意：真机/微信开发者工具中需先开通流量主并审核通过，否则 ad.show() 会报错。
 */
export const REWARDED_AD_UNIT_ID = 'adunit-xxxxxxxxxxxxxx'

/** 看完一次激励视频后上报，兑换对应能力的一次机会。type 对应后端白名单（当前仅 RESHUFFLE_UNLIMITED）。 */
export function watchAdReward(type: string, adUnitId?: string) {
  return post<{ granted: boolean; type: string }>('/ad/reward', { type, adUnitId })
}
