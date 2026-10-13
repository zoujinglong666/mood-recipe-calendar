"use strict";
const common_vendor = require("../common/vendor.js");
const api_favorites = require("../api/favorites.js");
const utils_toast = require("../utils/toast.js");
const utils_login = require("../utils/login.js");
const stores_user = require("../stores/user.js");
const favorites = common_vendor.ref([]);
let inflight = null;
function useWishlist() {
  function isFav(id) {
    return favorites.value.includes(id);
  }
  function syncFromServer() {
    if (!inflight) {
      inflight = api_favorites.fetchFavorites().then((list) => {
        favorites.value = list.map((f) => String(f.recipeId));
      }).catch(() => {
      }).finally(() => {
        inflight = null;
      });
    }
    return inflight;
  }
  async function toggle(id) {
    if (!stores_user.useUserStore().isLoggedIn) {
      utils_login.navigateToLogin();
      return;
    }
    const was = isFav(id);
    favorites.value = was ? favorites.value.filter((x) => x !== id) : [...favorites.value, id];
    try {
      if (was)
        await api_favorites.removeFavorite(id);
      else
        await api_favorites.addFavorite(id);
    } catch (e) {
      favorites.value = was ? [...favorites.value, id] : favorites.value.filter((x) => x !== id);
      utils_toast.toastError(e, "操作失败，请稍后重试");
    }
  }
  return { favorites, isFav, toggle, syncFromServer };
}
exports.useWishlist = useWishlist;
