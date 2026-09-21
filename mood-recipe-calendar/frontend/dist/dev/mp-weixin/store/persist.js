"use strict";
const common_vendor = require("../common/vendor.js");
function persist({ store }, excludedIds) {
  const isExcluded = excludedIds.includes(store.$id);
  if (isExcluded) {
    return;
  }
  let persistState = common_vendor.deepClone(store.$state);
  const storageState = common_vendor.index.getStorageSync(store.$id);
  if (storageState) {
    persistState = storageState;
  }
  store.$state = persistState;
  store.$subscribe(() => {
    common_vendor.index.setStorageSync(store.$id, common_vendor.deepClone(store.$state));
  });
}
function persistPlugin(context) {
  persist(context, ["temp", "theme"]);
}
exports.persistPlugin = persistPlugin;
