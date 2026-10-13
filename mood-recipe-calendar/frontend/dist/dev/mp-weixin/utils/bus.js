"use strict";
const listeners = {};
const MRC_EVENTS = {
  /** 食光记录新增/更新（日历需重拉 records 与连续打卡天数）。 */
  RECORDS_CHANGED: "mrc:records-changed",
  /** 周计划被换菜/反馈改动（周计划详情页需重拉）。携带 planId。 */
  PLAN_CHANGED: "mrc:plan-changed",
  /** 虚拟商品订单变化（订单列表需重拉）。 */
  ORDERS_CHANGED: "mrc:orders-changed"
};
const bus = {
  on(event, handler) {
    if (!listeners[event])
      listeners[event] = /* @__PURE__ */ new Set();
    listeners[event].add(handler);
  },
  off(event, handler) {
    var _a;
    (_a = listeners[event]) == null ? void 0 : _a.delete(handler);
  },
  emit(event, payload) {
    var _a;
    (_a = listeners[event]) == null ? void 0 : _a.forEach((handler) => {
      try {
        handler(payload);
      } catch (error) {
        console.error("[bus] handler error", event, error);
      }
    });
  }
};
exports.MRC_EVENTS = MRC_EVENTS;
exports.bus = bus;
