import { post } from './request'

/**
 * 看广告换能力（服务端记账发放）。
 * 广告位 ID 统一由 @/config/ads 的 AD_UNITS 提供（真实 ID 在微信流量主后台创建），
 * 播放由 composables/useRewardedVideoAd 负责（预加载/重试/看完才发奖/防连点）。
 * type 对应后端白名单（当前仅 RESHUFFLE_UNLIMITED）。
 */

/** 看完一次激励视频后上报，兑换对应能力的一次机会。 */
export function watchAdReward(type: string, adUnitId?: string) {
  return post<{ granted: boolean; type: string }>('/ad/reward', { type, adUnitId })
}
