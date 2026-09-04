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
  const sessionToken = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!openid.value && !!sessionToken.value)

  function setLogin(oid: string, token: string, info?: UserInfo) {
    openid.value = oid
    sessionToken.value = token
    if (info) userInfo.value = info
    uni.setStorageSync('openid', oid)
    uni.setStorageSync('sessionToken', token)
    if (info) uni.setStorageSync('userInfo', JSON.stringify(info))
  }

  function logout() {
    openid.value = ''
    sessionToken.value = ''
    userInfo.value = null
    uni.removeStorageSync('openid')
    uni.removeStorageSync('sessionToken')
    uni.removeStorageSync('userInfo')
  }

  function restoreFromStorage() {
    const oid = uni.getStorageSync('openid')
    const token = uni.getStorageSync('sessionToken')
    if (oid && token) {
      openid.value = oid
      sessionToken.value = token
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
    sessionToken,
    userInfo,
    isLoggedIn,
    setLogin,
    logout,
    restoreFromStorage,
  }
})
