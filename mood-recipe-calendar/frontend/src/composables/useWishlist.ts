import { ref } from 'vue'
import { fetchFavorites, addFavorite, removeFavorite } from '@/api/favorites'
import { toastError } from '@/utils/toast'
import { navigateToLogin } from '@/utils/login'
import { useUserStore } from '@/stores/user'

/** 收藏 id 列表（内存单例，各页面共享）；权威数据在后端，本地仅作乐观缓存。 */
const favorites = ref<string[]>([])
let inflight: Promise<void> | null = null

export function useWishlist() {
  function isFav(id: string) {
    return favorites.value.includes(id)
  }

  /** 页面 onShow 时调用：从后端同步收藏（并发去重，失败静默保留旧值）。 */
  function syncFromServer() {
    if (!inflight) {
      inflight = fetchFavorites()
        .then((list) => { favorites.value = list.map(f => String(f.recipeId)) })
        .catch(() => {})
        .finally(() => { inflight = null })
    }
    return inflight
  }

  /** 乐观更新 + 失败回滚；未登录先引导登录。 */
  async function toggle(id: string) {
    if (!useUserStore().isLoggedIn) {
      navigateToLogin()
      return
    }
    const was = isFav(id)
    favorites.value = was ? favorites.value.filter(x => x !== id) : [...favorites.value, id]
    try {
      if (was)
        await removeFavorite(id)
      else
        await addFavorite(id)
    }
    catch (e) {
      // 回滚到操作前状态（仅影响 isFav 判断，顺序无关紧要）
      favorites.value = was ? [...favorites.value, id] : favorites.value.filter(x => x !== id)
      toastError(e, '操作失败，请稍后重试')
    }
  }

  return { favorites, isFav, toggle, syncFromServer }
}
