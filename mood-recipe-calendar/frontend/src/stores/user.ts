import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UserInfo {
  id?: number
  openid: string
  nickname?: string
  avatarUrl?: string
  isMember?: number
}

export const useUserStore = defineStore('user', () => {
  const openid = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!openid.value)

  function setLogin(oid: string, info?: UserInfo) {
    openid.value = oid
    if (info) userInfo.value = info
    uni.setStorageSync('openid', oid)
    if (info) uni.setStorageSync('userInfo', JSON.stringify(info))
  }

  function logout() {
    openid.value = ''
    userInfo.value = null
    uni.removeStorageSync('openid')
    uni.removeStorageSync('userInfo')
  }

  function restoreFromStorage() {
    const oid = uni.getStorageSync('openid')
    if (oid) {
      openid.value = oid
      const infoStr = uni.getStorageSync('userInfo')
      if (infoStr) {
        try {
          userInfo.value = JSON.parse(infoStr)
        } catch {}
      }
    }
  }

  return {
    openid,
    userInfo,
    isLoggedIn,
    setLogin,
    logout,
    restoreFromStorage,
  }
})
