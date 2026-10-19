/**
 * 激励视频广告（微信流量主）可复用 hooks。
 *
 * 用法：
 * ```ts
 * const { show, showing } = useRewardedVideoAd(AD_UNITS.QUOTA_REWARD, {
 *   onReward: async () => { await grantQuotaFromAd() }, // 完整看完才回调，在这里做服务端发奖
 *   onCancel: () => toast('看完才能领奖励哦'),           // 中途关闭，不发奖
 * })
 * await show()
 * ```
 *
 * 设计要点（相对官方示例的改进）：
 * - 同 adUnitId 全局复用单例（模块级 Map）：官方示例每页各建实例会重复加载，
 *   单例让预加载只做一次、切页面不丢已加载广告；
 * - show() 返回 Promise<{ rewarded, closed }>：rewarded 即微信 onClose 的
 *   isEnded（完整看完），是唯一发奖依据；closed=false 表示加载/显示失败；
 * - 显示失败自动 load→show 重试一次（官方推荐模式），仍失败以 onError 收尾，
 *   调用方不会永远 pending；
 * - 防连点：同一 adUnitId 播放中忽略后续 show；
 * - 仅 MP-WEIXIN 生效；H5 等端 show() 直接返回失败并回调 onError；
 * - onReward 抛错会上浮为 show() 的 rejection（服务端发奖失败需调用方重试）。
 */
import { ref } from 'vue'

export interface RewardedVideoResult {
  /** 完整看完（isEnded）——唯一发奖依据 */
  rewarded: boolean
  /** 发生了关闭；false 表示加载/显示失败，用户根本没看到广告 */
  closed: boolean
}

interface RewardedVideoAdLike {
  load(): Promise<unknown>
  show(): Promise<unknown>
  onLoad(cb: () => void): void
  onError(cb: (err: any) => void): void
  onClose(cb: (res: { isEnded?: boolean }) => void): void
}

/** 同 adUnitId 复用同一实例：跨页面预加载只加载一次，切页不丢已加载广告 */
const adInstances = new Map<string, RewardedVideoAdLike>()
/** 每个 adUnitId 等待 onClose/onError 收场的 show 调用 */
const waiters = new Map<string, Array<(result: RewardedVideoResult) => void>>()
/** 播放中防连点 */
const playing = new Set<string>()

function resolveWaiters(adUnitId: string, result: RewardedVideoResult) {
  const list = waiters.get(adUnitId) || []
  waiters.delete(adUnitId)
  list.forEach(resolve => resolve(result))
}

function getRewardedVideoAd(adUnitId: string): RewardedVideoAdLike | null {
  // 注意：条件编译块顺序不能反——若把「#ifndef 非 MP 端 return null」放在函数开头，
  // tsc 会把后面的 MP 分支整块判为不可达代码并不做类型收窄（if (cached) return cached 会报
  // undefined 不可赋值）。MP 端保留上半段、非 MP 端只剩末尾的 return null。
  // #ifdef MP-WEIXIN
  const cached = adInstances.get(adUnitId)
  if (cached) return cached
  const wx = (globalThis as any).wx
  if (!wx || typeof wx.createRewardedVideoAd !== 'function') return null
  let ad: RewardedVideoAdLike
  try {
    ad = wx.createRewardedVideoAd({ adUnitId })
  }
  catch (err) {
    console.error('[rewarded-video] 创建实例失败', adUnitId, err)
    return null
  }
  ad.onError((err) => {
    console.error('[rewarded-video] 广告出错', adUnitId, err)
    resolveWaiters(adUnitId, { rewarded: false, closed: false })
  })
  ad.onClose((res) => {
    // 关闭后预加载下一条，保证下次 show 有库存
    ad.load().catch(() => {})
    resolveWaiters(adUnitId, { rewarded: !!res?.isEnded, closed: true })
  })
  adInstances.set(adUnitId, ad)
  // 创建即预加载：用户点按钮时大概率已有库存，缩短等待
  ad.load().catch(() => {})
  return ad
  // #endif
  // #ifndef MP-WEIXIN
  return null
  // #endif
}

export function useRewardedVideoAd(adUnitId: string, options: {
  /** 完整看完后才调用：在这里做服务端发奖；抛错会上浮为 show() 的 rejection */
  onReward?: () => void | Promise<void>
  /** 中途关闭：未看完不发奖 */
  onCancel?: () => void
  /** 广告加载/显示失败（用户没看到广告） */
  onError?: (err: Error) => void
} = {}) {
  const showing = ref(false)
  // 进入页面即创建实例并预加载：用户点按钮时大概率已有库存，缩短等待。
  // （官方示例在 onLoad 创建；setup 阶段等价且更早。创建失败不缓存，下次 show 会重试）
  getRewardedVideoAd(adUnitId)

  async function show(): Promise<RewardedVideoResult> {
    const ad = getRewardedVideoAd(adUnitId)
    if (!ad) {
      options.onError?.(new Error('当前环境不支持激励视频广告'))
      return { rewarded: false, closed: false }
    }
    if (playing.has(adUnitId))
      return { rewarded: false, closed: false }

    playing.add(adUnitId)
    showing.value = true
    try {
      const result = await new Promise<RewardedVideoResult>((resolve) => {
        const list = waiters.get(adUnitId) || []
        list.push(resolve)
        waiters.set(adUnitId, list)
        ad.show().catch(() => {
          // 官方推荐重试模式：显示失败先 load 再 show 一次
          ad.load()
            .then(() => ad.show())
            .catch((err) => {
              console.error('[rewarded-video] 显示失败', adUnitId, err)
              resolveWaiters(adUnitId, { rewarded: false, closed: false })
            })
        })
      })
      if (result.rewarded)
        await options.onReward?.()
      else if (result.closed)
        options.onCancel?.()
      else
        options.onError?.(new Error('广告还没加载好，稍后再试'))
      return result
    }
    finally {
      playing.delete(adUnitId)
      showing.value = false
    }
  }

  return { show, showing }
}
