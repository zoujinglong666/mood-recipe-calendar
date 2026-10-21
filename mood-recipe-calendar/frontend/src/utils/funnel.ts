/**
 * P1 漏斗分析埋点：首页 → 点推荐 → 拿到菜 → 记录完
 * 运营扩展事件：分享 / 广告 / 会员付费 / 餐别选择（拉新与变现双链路）
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
  // ---------- 运营扩展（注册后填真实 event_id） ----------
  share_click: '', // 点分享按钮（拉新链路入口）
  ad_watch: '', // 看完一次激励视频（变现链路，type=奖励类型）
  member_view: '', // 会员页曝光（付费漏斗入口）
  member_pay: '', // 会员支付成功（付费漏斗终点）
  meal_sheet_pick: '', // 餐别选择完成（推荐链路关键分叉）
  qrcode_scan: '', // 小程序码扫码进入（分享物料归因，scene=来源）
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
