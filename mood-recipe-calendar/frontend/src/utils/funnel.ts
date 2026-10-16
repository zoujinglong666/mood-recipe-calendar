/**
 * P1 漏斗分析埋点：首页 → 点推荐 → 拿到菜 → 记录完
 *
 * 用法：funnel('recommend_click')
 * 事件 ID 需要先在微信公众平台「We分析 → 行为分析 → 事件管理」注册，
 * 注册后把下面 PLACEHOLDER_xxx 换成真实 event_id 即可，业务代码不用改。
 *
 * 当前未注册时会走 console.log 兜底，方便本地看链路。
 */

// 微信公众平台「事件管理」里注册后填这里；留空则只打 console，不上报
const EVENT_IDS: Record<string, string> = {
  home_view: '', // 进入首页
  recommend_click: '', // 点"今天吃什么"
  recipe_delivered: '', // 拿到一道菜
  record_saved: '', // 记录完一顿饭
}

export function funnel(event: keyof typeof EVENT_IDS, params: Record<string, string | number> = {}) {
  const id = EVENT_IDS[event]
  const payload = { event, ts: Date.now(), ...params }
  // #ifdef MP-WEIXIN
  try {
    const rpt = (uni as any).reportEvent
    if (typeof rpt === 'function' && id) {
      rpt(id, params)
    } else {
      // 未注册事件：降级 console，方便本地调试时看漏斗
      console.log('[funnel]', JSON.stringify(payload))
    }
  } catch {
    // 埋点不能影响主流程
  }
  // #endif
  // #ifndef MP-WEIXIN
  console.log('[funnel]', JSON.stringify(payload))
  // #endif
}
