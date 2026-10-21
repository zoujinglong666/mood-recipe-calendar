import { get } from './request'

/**
 * 小程序码（「扫码打开小程序」真码，后端 wxacode.getUnlimited 生成）。
 * 替代原先导出图里嵌的静态占位 JPG 和页面上的 CSS 假网格——好友扫码什么都扫不出。
 *
 * scene 是分享物料来源（album/report/recipe/daily_menu/record），
 * 落地统一首页；首页 onLoad 读 query.scene 做扫码归因。
 */

/** storage key 带版本：后端换 COS 域名等导致 URL 变化时，升版本号即可全体失效 */
const CACHE_KEY = (scene: string) => `mrc_wxacode_v1_${scene}`

/** 取小程序码 URL：storage 永久缓存（scene 固定 → 码固定 → URL 固定），没有则请求后端 */
export async function fetchWxacodeUrl(scene: string): Promise<string> {
  const key = CACHE_KEY(scene)
  const cached = uni.getStorageSync(key)
  if (cached) return cached as string
  const res = await get<{ url: string }>('/api/wxacode', { scene })
  if (res?.url) {
    uni.setStorageSync(key, res.url)
    return res.url
  }
  return ''
}

/** 导出/展示前的码 URL 准备：读缓存或请求；任何失败返回空串，调用方走文字兜底 */
export async function ensureWxacodeUrl(scene: string): Promise<string> {
  try {
    return await fetchWxacodeUrl(scene)
  }
  catch {
    return ''
  }
}
