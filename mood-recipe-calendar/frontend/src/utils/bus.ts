/**
 * 轻量跨页面事件总线。
 *
 * 用意在替代"改完数据相关页面不回写"的割裂体验：
 * 记录一条、反馈一道菜、买完权益、换一道菜后，相关页面（日历、周计划、订单）
 * 通过统一的事件感知到数据已变化并主动刷新，而不是依赖用户手动重进。
 *
 * uni-app 的 uni.$on/uni.$emit 在 tab 页切换时行为不稳定，这里用独立集合，
 * 订阅方必须在 onUnmounted 中 off，避免泄漏。
 */

type Handler = (payload?: any) => void

const listeners: Record<string, Set<Handler>> = {}

export const MRC_EVENTS = {
  /** 食光记录新增/更新（日历需重拉 records 与连续打卡天数）。 */
  RECORDS_CHANGED: 'mrc:records-changed',
  /** 周计划被换菜/反馈改动（周计划详情页需重拉）。携带 planId。 */
  PLAN_CHANGED: 'mrc:plan-changed',
  /** 虚拟商品订单变化（订单列表需重拉）。 */
  ORDERS_CHANGED: 'mrc:orders-changed',
} as const

export const bus = {
  on(event: string, handler: Handler) {
    if (!listeners[event])
      listeners[event] = new Set()
    listeners[event].add(handler)
  },
  off(event: string, handler: Handler) {
    listeners[event]?.delete(handler)
  },
  emit(event: string, payload?: any) {
    listeners[event]?.forEach((handler) => {
      try {
        handler(payload)
      }
      catch (error) {
        console.error('[bus] handler error', event, error)
      }
    })
  },
}
