/**
 * 全局导航锁。
 *
 * 背景：小程序里快速连点（或上一次 navigateTo 还没 success/fail 就再次发起）
 * 会让两次导航并发，前一次完成时它的 webview 已被后一次替换，
 * 微信抛「routeDone with a webviewId ... is not found」——白屏/页面卡死的来源之一。
 *
 * 策略：
 * - pending：上一次导航尚未 success/fail 时，直接丢弃新的导航意图；
 * - 时间窗：距上次发起不足 600ms，同样丢弃（治连点）。
 *
 * switchTab/reLaunch 会重置页面栈、无此竞态，不归本锁管辖（调用方按需直接用）。
 */

/** 连点合并窗口 */
const NAV_LOCK_MS = 300
/** pending 兜底释放：极端情况下导航回调不触发（页面被销毁等），超时后自动解锁，避免永久卡死 */
const NAV_PENDING_TIMEOUT = 3000

let lastNavAt = 0
let pending = 0

/**
 * 带锁执行一次导航。run 需返回导航 Promise（router.push / router.pushTab 均返回 Promise）；
 * 若处于 pending 中或落在 600ms 时间窗内，本次调用被静默丢弃。
 */
export function navLocked(run: () => Promise<unknown> | void): void {
  if (pending > 0)
    return
  const now = Date.now()
  if (now - lastNavAt < NAV_LOCK_MS)
    return
  lastNavAt = now
  pending++

  let settled = false
  const settle = () => {
    if (settled)
      return
    settled = true
    pending = Math.max(0, pending - 1)
  }

  let result: Promise<unknown> | void
  try {
    result = run()
  }
  catch {
    settle()
    return
  }

  if (result && typeof (result as Promise<unknown>).then === 'function') {
    const timer = setTimeout(settle, NAV_PENDING_TIMEOUT)
    ;(result as Promise<unknown>).then(
      () => {
        clearTimeout(timer)
        settle()
      },
      () => {
        clearTimeout(timer)
        settle()
      },
    )
  }
  else {
    settle()
  }
}
