import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export interface UserInfo {
  id?: number
  openid: string
  nickname?: string
  avatarUrl?: string
  isMember?: number
  memberExpire?: string
}

export const useUserStore = defineStore('user', () => {
  const openid = ref<string>('')
  const sessionToken = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)
  /** 用户主动退出登录的标记：退出后页面 onShow 自动调 ensureLogin 时不应静默登录 */
  const userInitiatedLogout = ref(false)

  const isLoggedIn = computed(() => !!openid.value && !!sessionToken.value)

  /** 有效会员：isMember=1 且会员未过期。会员门禁的唯一真相源。 */
  const isActiveMember = computed(() => {
    const info = userInfo.value
    if (!info || info.isMember !== 1 || !info.memberExpire)
      return false
    return new Date(info.memberExpire).getTime() > Date.now()
  })

  function setLogin(oid: string, token: string, info?: UserInfo) {
    openid.value = oid
    sessionToken.value = token
    userInitiatedLogout.value = false
    if (info) userInfo.value = info
    uni.setStorageSync('openid', oid)
    uni.setStorageSync('sessionToken', token)
    if (info) uni.setStorageSync('userInfo', JSON.stringify(info))
  }

  function logout() {
    openid.value = ''
    sessionToken.value = ''
    userInfo.value = null
    userInitiatedLogout.value = true
    uni.removeStorageSync('openid')
    uni.removeStorageSync('sessionToken')
    uni.removeStorageSync('userInfo')
  }

  function clearLogoutFlag() {
    userInitiatedLogout.value = false
  }

  function restoreFromStorage() {
    const oid = uni.getStorageSync('openid')
    const token = uni.getStorageSync('sessionToken')
    if (oid && token) {
      openid.value = oid
      sessionToken.value = token
      userInitiatedLogout.value = false
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
    isActiveMember,
    userInitiatedLogout,
    setLogin,
    logout,
    clearLogoutFlag,
    restoreFromStorage,
  }
})
