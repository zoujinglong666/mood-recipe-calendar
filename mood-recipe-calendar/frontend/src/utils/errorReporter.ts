import { post } from '@/api/request'

/**
 * [2026-10-26] 前端错误上报（架构评审 P1-4）。
 *
 * 在 App.vue 的 onError / uni.onUnhandledRejection 中调用，POST /api/client-errors，
 * 服务端去重后推 WxPusher 告警——此前前端白屏 / JS 报错用户只会流失，后端完全无感知。
 *
 * 设计要点：
 * - fire-and-forget：上报自身绝不能抛错、绝不阻塞用户流程；
 * - 同指纹（页面+消息）60 秒内只上报一次，服务端还有 10 分钟窗口二次去重；
 * - 字段截断与服务端上限对齐（message 500 / stack 2000）。
 */
const DEDUPE_WINDOW_MS = 60_000
const recentFingerprints = new Map<string, number>()

export function reportClientError(kind: string, message: string, stack?: string): void {
  try {
    const pages = getCurrentPages()
    const page = pages.length ? (pages[pages.length - 1].route ?? 'unknown') : 'unknown'
    const fingerprint = `${page}|${message}`
    const now = Date.now()
    const last = recentFingerprints.get(fingerprint)
    if (last && now - last < DEDUPE_WINDOW_MS) return
    recentFingerprints.set(fingerprint, now)
    if (recentFingerprints.size > 50) {
      for (const [key, time] of recentFingerprints) {
        if (now - time > DEDUPE_WINDOW_MS) recentFingerprints.delete(key)
      }
    }
    post('/client-errors', {
      kind,
      message: String(message ?? '').slice(0, 500),
      stack: stack ? String(stack).slice(0, 2000) : '',
      page,
    }, 10_000).catch(() => { /* 上报失败静默，避免错误风暴 */ })
  }
  catch {
    // 上报本身抛错时彻底吞掉，绝不产生二次异常
  }
}
