<script setup lang="ts">
import type { ThemeMode } from '@/composables/useManualTheme'
import { computed, ref } from 'vue'
import { logout as apiLogout, updateUserInfo } from '@/api/auth'
import { uploadFile } from '@/api/request'
import Icon from '@/components/common/Icon.vue'
import { useManualTheme } from '@/composables/useManualTheme'
import { navBack } from '@/composables/useNavBar'
import { useUserStore } from '@/stores/user'
import { ensureLogin, refreshUserInfo } from '@/utils/login'
import { toast, toastError, toastSuccess } from '@/utils/toast'

definePage({
  name: 'settings',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '设置' },
})

type ThemeChoice = ThemeMode | 'system'
const THEME_CHOICES: { value: ThemeChoice, label: string, icon: 'phone' | 'user' | 'moon' }[] = [
  { value: 'system', label: '跟随系统', icon: 'phone' },
  { value: 'light', label: '浅色', icon: 'user' },
  { value: 'dark', label: '深色', icon: 'moon' },
]

const userStore = useUserStore()
const { isDark, followSystem, currentThemeColor, themeColorOptions, toggleTheme, setFollowSystem, selectThemeColor } = useManualTheme()
const nickname = ref('')
const avatarUpdating = ref(false)
const nicknameSaving = ref(false)
const loginLoading = ref(false)
const logoutLoading = ref(false)

const avatar = computed(() => userStore.userInfo?.avatarUrl || '/static/guozai/mood_01_happy.png')
const themeChoice = computed<ThemeChoice>(() => followSystem.value ? 'system' : isDark.value ? 'dark' : 'light')

onShow(async () => {
  userStore.restoreFromStorage()
  if (userStore.isLoggedIn)
    await refreshUserInfo()
  nickname.value = userStore.userInfo?.nickname || ''
})

async function loginNow() {
  if (loginLoading.value)
    return
  loginLoading.value = true
  try {
    await ensureLogin()
    await refreshUserInfo()
    nickname.value = userStore.userInfo?.nickname || ''
    toastSuccess('微信身份已连接')
  }
  catch (error) {
    toastError(error, '登录失败，请稍后重试')
  }
  finally {
    loginLoading.value = false
  }
}

async function updateAvatar(filePath: string) {
  if (!userStore.isLoggedIn) {
    toast('请先登录再修改头像')
    return
  }
  if (!filePath || avatarUpdating.value)
    return
  avatarUpdating.value = true
  try {
    const uploaded = await uploadFile(filePath)
    await updateUserInfo({ openid: userStore.openid, avatarUrl: uploaded.url })
    await refreshUserInfo()
    toastSuccess('头像已更新')
  }
  catch (error) {
    toastError(error, '头像更新失败，请重试')
  }
  finally {
    avatarUpdating.value = false
  }
}

function onChooseAvatar(event: any) {
  updateAvatar(event.detail?.avatarUrl || '')
}

function chooseH5Avatar() {
  if (!userStore.isLoggedIn) {
    toast('请先登录再修改头像')
    return
  }
  uni.chooseImage({
    count: 1,
    success: result => updateAvatar(String(result.tempFilePaths?.[0] || '')),
    fail: (error) => {
      if (!/cancel/i.test(error.errMsg || ''))
        toast('未能读取图片，请重试')
    },
  })
}

async function saveNickname() {
  const value = nickname.value.trim()
  if (!userStore.isLoggedIn) {
    toast('请先登录再修改昵称')
    return
  }
  if (!value) {
    toast('昵称不能为空')
    return
  }
  if (nicknameSaving.value || value === userStore.userInfo?.nickname)
    return
  nicknameSaving.value = true
  try {
    await updateUserInfo({ openid: userStore.openid, nickname: value })
    await refreshUserInfo()
    nickname.value = userStore.userInfo?.nickname || value
    toastSuccess('昵称已保存')
  }
  catch (error) {
    nickname.value = userStore.userInfo?.nickname || ''
    toastError(error, '昵称保存失败，请重试')
  }
  finally {
    nicknameSaving.value = false
  }
}

function chooseTheme(value: ThemeChoice) {
  if (value === 'system') {
    setFollowSystem(true)
    return
  }
  setFollowSystem(false)
  toggleTheme(value)
}

function askForLogout() {
  if (!userStore.isLoggedIn || logoutLoading.value)
    return
  uni.showModal({
    title: '退出登录？',
    content: '退出后本机将清除你的登录信息，下次使用需要重新连接微信身份。',
    confirmText: '退出登录',
    confirmColor: '#D94841',
    success: (result) => {
      if (result.confirm)
        performLogout()
    },
  })
}

async function performLogout() {
  logoutLoading.value = true
  try {
    await apiLogout().catch(() => {}) // 后端登出失败不影响本地清除
  } finally {
    userStore.logout()
    nickname.value = ''
    logoutLoading.value = false
    toastSuccess('已退出登录')
  }
}
</script>

<template>
  <view class="settings-page">
    <wd-navbar title="设置" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="settings-content">
      <view class="settings-intro">
        <view>
          <text class="settings-intro__eyebrow">
            锅仔的小饭友
          </text>
          <text class="settings-intro__title">
            把这里调成<br>最舒服的样子
          </text>
        </view>
        <image class="settings-intro__image guozai-breathe" src="/static/guozai/action_06_glasses.png" mode="aspectFit" />
      </view>

      <view class="settings-section">
        <view class="settings-heading">
          <text class="settings-heading__title">
            用户基本信息
          </text>
          <text class="settings-heading__hint">
            头像和昵称会同步到“我的”
          </text>
        </view>

        <view class="account-card">
          <!-- #ifdef MP-WEIXIN -->
          <button class="avatar-button" open-type="chooseAvatar" :disabled="avatarUpdating" aria-label="更换头像" @chooseavatar="onChooseAvatar">
            <view class="account-avatar">
              <image :src="avatar" :mode="userStore.userInfo?.avatarUrl ? 'aspectFill' : 'aspectFit'" />
              <view class="account-avatar__badge">
                <Icon name="camera" :size="26" color="#EF5A3C" />
              </view>
              <view v-if="avatarUpdating" class="account-avatar__loading">
                上传中
              </view>
            </view>
          </button>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <view class="avatar-button" role="button" aria-label="更换头像" @click="chooseH5Avatar">
            <view class="account-avatar">
              <image :src="avatar" :mode="userStore.userInfo?.avatarUrl ? 'aspectFill' : 'aspectFit'" />
              <view class="account-avatar__badge">
                <Icon name="camera" :size="26" color="#EF5A3C" />
              </view>
              <view v-if="avatarUpdating" class="account-avatar__loading">
                上传中
              </view>
            </view>
          </view>
          <!-- #endif -->

          <view class="account-main">
            <view class="account-state">
              <text class="account-state__dot" :class="{ 'is-online': userStore.isLoggedIn }" /><text>{{ userStore.isLoggedIn ? '微信身份已连接' : '当前未登录' }}</text>
            </view>
            <template v-if="userStore.isLoggedIn">
              <text class="field-label">
                昵称
              </text>
              <!-- #ifdef MP-WEIXIN -->
              <input v-model="nickname" class="nickname-input" type="nickname" :maxlength="24" placeholder="给自己取个昵称" confirm-type="done" @confirm="saveNickname">
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
              <input v-model="nickname" class="nickname-input" type="text" :maxlength="24" placeholder="给自己取个昵称" confirm-type="done" @confirm="saveNickname">
              <!-- #endif -->
              <view class="nickname-save pressable" :class="{ 'is-disabled': nicknameSaving || !nickname.trim() || nickname.trim() === userStore.userInfo?.nickname }" role="button" aria-label="保存昵称" @click="saveNickname">
                {{ nicknameSaving ? '保存中…' : '保存昵称' }}
              </view>
            </template>
            <view v-else class="login-button pressable" :class="{ 'is-disabled': loginLoading }" role="button" aria-label="微信登录" @click="loginNow">
              {{ loginLoading ? '连接中…' : '微信登录' }}
            </view>
          </view>
        </view>
      </view>

      <view class="settings-section">
        <view class="settings-heading">
          <text class="settings-heading__title">
            主题模式
          </text><text class="settings-heading__hint">
            选择你看着最舒服的外观
          </text>
        </view>
        <view class="theme-card">
          <view class="theme-options" aria-label="主题模式">
            <view v-for="option in THEME_CHOICES" :key="option.value" class="theme-option pressable" :class="{ 'is-selected': themeChoice === option.value }" role="button" :aria-label="`切换为${option.label}`" @click="chooseTheme(option.value)">
              <Icon :name="option.icon" :size="34" :color="themeChoice === option.value ? '#EF5A3C' : '#A1826A'" />
              <text>{{ option.label }}</text>
              <text class="theme-option__check">
                {{ themeChoice === option.value ? '✓' : '' }}
              </text>
            </view>
          </view>
          <view class="theme-colors">
            <text class="field-label">
              主题色
            </text>
            <view class="theme-colors__list">
              <view v-for="option in themeColorOptions" :key="option.value" class="color-option pressable" :class="{ 'is-selected': currentThemeColor?.value === option.value }" role="button" :aria-label="`使用${option.name}主题色`" @click="selectThemeColor(option)">
                <text class="color-option__dot" :style="{ background: option.primary }" />
                <text class="color-option__name">
                  {{ option.name }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-if="userStore.isLoggedIn" class="logout-button pressable" :class="{ 'is-disabled': logoutLoading }" role="button" aria-label="退出登录" @click="askForLogout">
        {{ logoutLoading ? '正在退出…' : '退出登录' }}
      </view>
      <text class="settings-footnote">
        你的口味记忆与菜谱记录保存在账号中，退出登录不会删除数据。
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.settings-page { min-height: 100vh; min-height: 100dvh; box-sizing: border-box; color: var(--mrc-text); background: var(--mrc-bg); }
.settings-content { width: calc(100% - 48rpx); max-width: 820rpx; margin: 0 auto; padding: 12rpx 0 calc(52rpx + env(safe-area-inset-bottom)); }
.settings-intro { position: relative; display: flex; min-height: 220rpx; align-items: center; overflow: hidden; padding: 28rpx 30rpx; box-sizing: border-box; border: 2rpx solid var(--mrc-border-light); border-radius: 36rpx; background: linear-gradient(135deg, var(--mrc-surface), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft); }
.settings-intro__eyebrow { display: block; color: var(--mrc-accent); font-size: 20rpx; font-weight: 700; letter-spacing: 2rpx; }
.settings-intro__title { display: block; margin-top: 10rpx; color: var(--mrc-text-strong); font-size: 38rpx; font-weight: 800; line-height: 1.35; }
.settings-intro__image { position: absolute; right: -8rpx; bottom: -12rpx; width: 230rpx; height: 230rpx; }
.settings-section { margin-top: 36rpx; }
.settings-heading { margin: 0 4rpx 16rpx; }
.settings-heading__title, .settings-heading__hint { display: block; }
.settings-heading__title { color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; }
.settings-heading__hint { margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.account-card, .theme-card { padding: 26rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.account-card { display: flex; align-items: flex-start; gap: 26rpx; }
.avatar-button { width: 136rpx; height: 136rpx; flex-shrink: 0; margin: 0; padding: 0; border: 0; border-radius: 50%; background: transparent; line-height: 1; }
.avatar-button::after { border: 0; }
.account-avatar { position: relative; display: flex; width: 136rpx; height: 136rpx; align-items: center; justify-content: center; border: 4rpx solid var(--mrc-surface-peach); border-radius: 50%; background: var(--mrc-surface-peach); box-shadow: var(--mrc-shadow-sm); }
.account-avatar image { width: 126rpx; height: 126rpx; border-radius: 50%; }
.account-avatar__badge { position: absolute; right: -4rpx; bottom: -4rpx; display: flex; width: 48rpx; height: 48rpx; align-items: center; justify-content: center; border: 3rpx solid var(--mrc-surface); border-radius: 50%; background: var(--mrc-surface-sun); }
.account-avatar__loading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; border-radius: 50%; color: #fff; background: rgba(40, 24, 16, .62); font-size: 20rpx; font-weight: 700; }
.account-main { display: flex; flex: 1; min-width: 0; flex-direction: column; }
.account-state { display: flex; min-height: 44rpx; align-items: center; gap: 10rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.account-state__dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--mrc-text-light); }
.account-state__dot.is-online { background: var(--mrc-mint); box-shadow: 0 0 0 6rpx rgba(40, 194, 160, .12); }
.field-label { display: block; margin-top: 12rpx; color: var(--mrc-text-deep); font-size: 23rpx; font-weight: 700; }
.nickname-input { height: 76rpx; border-bottom: 2rpx solid var(--mrc-border); color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 700; }
.nickname-save, .login-button { display: flex; min-height: 88rpx; align-items: center; justify-content: center; margin-top: 14rpx; border-radius: 44rpx; color: #fff; background: var(--mrc-primary-grad); font-size: 27rpx; font-weight: 700; }
.theme-card { padding-top: 12rpx; }
.theme-options { display: flex; flex-direction: column; }
.theme-option { display: flex; min-height: 96rpx; align-items: center; gap: 16rpx; border-bottom: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 700; }
.theme-option:last-child { border-bottom: 0; }
.theme-option.is-selected { color: var(--mrc-accent); }
.theme-option__check { margin-left: auto; color: var(--mrc-accent); font-size: 30rpx; font-weight: 800; }
.theme-colors { padding-top: 20rpx; border-top: 2rpx solid var(--mrc-border-light); }
.theme-colors__list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14rpx; margin-top: 16rpx; }
.color-option { display: flex; min-height: 88rpx; align-items: center; gap: 10rpx; padding: 0 12rpx; box-sizing: border-box; border: 2rpx solid transparent; border-radius: 20rpx; color: var(--mrc-text-sub); background: var(--mrc-surface-2); }
.color-option.is-selected { border-color: var(--mrc-accent); color: var(--mrc-text-deep); }
.color-option__dot { width: 26rpx; height: 26rpx; flex-shrink: 0; border: 3rpx solid var(--mrc-surface); border-radius: 50%; box-shadow: var(--mrc-shadow-sm); }
.color-option__name { overflow: hidden; font-size: 21rpx; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.logout-button { display: flex; min-height: 96rpx; align-items: center; justify-content: center; margin-top: 44rpx; border: 2rpx solid rgba(217, 72, 65, .42); border-radius: 48rpx; color: #D94841; background: var(--mrc-surface); font-size: 28rpx; font-weight: 700; }
.settings-footnote { display: block; padding: 18rpx 28rpx 0; color: var(--mrc-text-light); font-size: 21rpx; line-height: 1.55; text-align: center; }
.pressable { transition: transform 180ms ease, opacity 180ms ease; }
.pressable:active { transform: scale(.97); }
.is-disabled { opacity: .48; pointer-events: none; }
@media (max-width: 350px) { .settings-content { width: calc(100% - 32rpx); } .settings-intro__image { width: 190rpx; height: 190rpx; opacity: .76; } .account-card { gap: 18rpx; padding: 22rpx; } .theme-colors__list { grid-template-columns: repeat(2, 1fr); } }
@media (prefers-reduced-motion: reduce) { .pressable { transition: none; } .pressable:active { transform: none; } }
</style>
