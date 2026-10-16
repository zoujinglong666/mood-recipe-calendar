"use strict";
const common_vendor = require("../common/vendor.js");
const EVENT_IDS = {
  home_view: "",
  // 进入首页
  recommend_click: "",
  // 点"今天吃什么"
  recipe_delivered: "",
  // 拿到一道菜
  record_saved: ""
  // 记录完一顿饭
};
function funnel(event, params = {}) {
  const id = EVENT_IDS[event];
  const payload = { event, ts: Date.now(), ...params };
  try {
    const rpt = common_vendor.index.reportEvent;
    if (typeof rpt === "function" && id) {
      rpt(id, params);
    } else {
      console.log("[funnel]", JSON.stringify(payload));
    }
  } catch {
  }
}
exports.funnel = funnel;
