<script setup lang="ts">
import type { ApiBackend } from '@/api/request'
import type { ThemeMode } from '@/composables/useManualTheme'
import { computed, ref } from 'vue'
import { logout as apiLogout, deleteAccount } from '@/api/auth'
import { API_BACKENDS, getApiBaseUrl, setApiBaseUrl } from '@/api/request'
import Icon from '@/components/common/Icon.vue'
import { useManualTheme } from '@/composables/useManualTheme'
import { navBack } from '@/composables/useNavBar'
import { useUserStore } from '@/stores/user'
import { STATIC_BASE_URL } from '@/utils/assets'
import { refreshUserInfo } from '@/utils/login'
import { adminCheck } from '@/api/admin'
import { toast, toastError, toastSuccess } from '@/utils/toast'

definePage({
  name: 'settings',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '设置' },
})

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'

// 直接本页写生命周期，比 useShare 组合式更可靠
onShareAppMessage(() => {
  const pages = getCurrentPages()
  const route = (pages[pages.length - 1] as any)?.route || ''
  return { title: '锅仔 · 按心情帮你决定今天吃什么', path: `/${route}` }
})
onShareTimeline(() => ({ title: '锅仔 · 按心情帮你决定今天吃什么' }))

type ThemeChoice = ThemeMode | 'system'
const THEME_CHOICES: { value: ThemeChoice, label: string, icon: 'phone' | 'user' | 'moon' }[] = [
  { value: 'system', label: '跟随系统', icon: 'phone' },
  { value: 'light', label: '浅色', icon: 'user' },
  { value: 'dark', label: '深色', icon: 'moon' },
]

const userStore = useUserStore()
const router = useRouter()
const { isDark, followSystem, currentThemeColor, themeColorOptions, toggleTheme, setFollowSystem, selectThemeColor } = useManualTheme()
const logoutLoading = ref(false)
const deleteLoading = ref(false)
const adminVisible = ref(false)


/** 检查更新（逻辑在 utils/update，微信原生分发，零维护） */
function goAdminRecipes() { router.push({ name: 'admin-recipes' }) }
function goAbout() { router.push({ name: 'about' }) }
function goAdminFeedback() { router.push({ name: 'admin-feedback' }) }

/** 后端服务切换 */
const currentBackendUrl = ref(getApiBaseUrl())
const currentBackend = computed(() => API_BACKENDS.find(b => b.url === currentBackendUrl.value) || {
  name: '自定义',
  url: currentBackendUrl.value,
  env: 'custom',
  desc: currentBackendUrl.value,
})
const switchingBackend = ref(false)

/** 设置页是否显示「后端服务」切换入口：生产构建仅单一生产环境、无可选项，默认隐藏；开发/联调构建可见。可用 VITE_SHOW_BACKEND_SWITCHER=true 强制显示（调试真机联调用）。 */
const showBackendSwitcher = !import.meta.env.PROD
  || String(import.meta.env.VITE_SHOW_BACKEND_SWITCHER) === 'true'

function selectBackend(backend: ApiBackend) {
  if (switchingBackend.value || backend.url === getApiBaseUrl())
    return
  uni.showModal({
    title: '切换后端服务',
    content: `将切换到「${backend.name}」（${backend.desc}）。切换后需要重新登录。`,
    confirmText: '切换',
    confirmColor: '#EF5A3C',
    success: (result) => {
      if (!result.confirm)
        return
      switchingBackend.value = true
      try {
        setApiBaseUrl(backend.url)
        currentBackendUrl.value = backend.url
        userStore.logout() // token 与后端绑定，切换后必须重新登录
        toastSuccess(`已切换到「${backend.name}」`)
      }
      finally {
        switchingBackend.value = false
      }
    },
  })
}

onShow(async () => {
  userStore.restoreFromStorage()
  if (userStore.isLoggedIn) {
    await refreshUserInfo()
    try { adminVisible.value = !!(await adminCheck()).admin } catch (e) { adminVisible.value = false }
  }
})

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
  }
  finally {
    userStore.logout()
    logoutLoading.value = false
    // 退出即回登录页：账号域操作完成，不把用户留在已登出的设置页
    uni.reLaunch({ url: '/pages/login/index' })
  }
}

function askDeleteAccount() {
  if (deleteLoading.value)
    return
  uni.showModal({
    title: '永久注销账号？',
    content: '这会删除菜谱记录、图片、偏好和锅仔记忆，且无法恢复。依法需保留的订单会去标识化保存。',
    confirmText: '永久注销',
    confirmColor: '#D94841',
    success: async (result) => {
      if (!result.confirm)
        return
      deleteLoading.value = true
      try {
        await deleteAccount()
        userStore.logout()
        toastSuccess('账号已注销')
        // 注销后同样是未登录态：与退出登录一致，直达登录页
        setTimeout(() => uni.reLaunch({ url: '/pages/login/index' }), 500)
      }
      catch (error) {
        toastError(error, '注销失败，数据尚未删除，请重试')
      }
      finally {
        deleteLoading.value = false
      }
    },
  })
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
          <text class="settings-intro__title">把这里调成</text>
          <text class="settings-intro__title">最舒服的样子</text>
        </view>
        <image class="settings-intro__image guozai-breathe" :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" mode="aspectFit" />
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

      <view v-if="showBackendSwitcher" class="settings-section">
        <view class="settings-heading">
          <text class="settings-heading__title">
            后端服务
          </text><text class="settings-heading__hint">
            切换后需要重新登录
          </text>
        </view>
        <view class="backend-card">
          <view class="backend-current">
            <Icon name="gear" :size="30" color="#EF5A3C" />
            <view class="backend-current__text">
              <text class="backend-current__name">
                当前：{{ currentBackend.name }}
              </text>
              <text class="backend-current__url">
                {{ currentBackend.url }}
              </text>
            </view>
          </view>
          <view class="backend-options" aria-label="后端服务">
            <view v-for="option in API_BACKENDS" :key="option.url" class="backend-option pressable" :class="{ 'is-selected': currentBackendUrl === option.url }" role="button" :aria-label="`切换到${option.name}`" @click="selectBackend(option)">
              <view class="backend-option__main">
                <text class="backend-option__name">
                  {{ option.name }}
                </text>
                <text class="backend-option__desc">
                  {{ option.desc }}
                </text>
              </view>
              <text class="backend-option__check">
                {{ currentBackendUrl === option.url ? '✓' : '' }}
              </text>
            </view>
          </view>
          <text class="backend-note">
            开发环境请选「本地开发」，正式发布前务必切回「生产环境」。
          </text>
        </view>
      </view>

      <view class="settings-section" v-if="adminVisible">
        <view class="settings-heading">
          <text class="settings-heading__title">管理后台</text>
        </view>
        <view class="about-card">
          <view class="about-row about-check pressable" role="button" aria-label="菜谱管理" @click="goAdminRecipes">
            <Icon name="info" :size="30" color="#EF5A3C" />
            <view class="about-row__text">
              <text class="about-row__name">菜谱管理中心</text>
              <text class="about-row__desc">审核/编辑/删除菜谱，替换占位假图</text>
            </view>
            <text class="about-row__arrow">›</text>
          </view>
          <view class="about-row about-check pressable" role="button" aria-label="用户反馈中心" @click="goAdminFeedback">
            <Icon name="book" :size="30" color="#EF5A3C" />
            <view class="about-row__text">
              <text class="about-row__name">用户反馈中心</text>
              <text class="about-row__desc">查看/处理用户反馈，流转处理状态</text>
            </view>
            <text class="about-row__arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 会员：从「我的」页 C 位降级为设置页一行。0 付费用户阶段不占首屏，入口保留可找回 -->
      <view class="settings-section">
        <view class="settings-heading">
          <text class="settings-heading__title">会员</text>
        </view>
        <view class="about-card">
          <view class="about-row about-check pressable" role="button" aria-label="查看锅仔会员权益" @click="router.push({ name: 'membership' })">
            <Icon name="vip" :size="30" color="#EF5A3C" />
            <view class="about-row__text">
              <text class="about-row__name">锅仔会员</text>
              <text class="about-row__desc">{{ userStore.isActiveMember ? '会员陪伴中 · 管饭智能体与高清画册已解锁' : '管饭智能体 · 私人菜单 · 高清画册' }}</text>
            </view>
            <text class="about-row__arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 关于我们：轻入口，内容（初心/检查更新/隐私/反馈）全部在关于页，设置页只做导航 -->
      <view class="settings-section">
        <view class="settings-heading">
          <text class="settings-heading__title">关于</text>
        </view>
        <view class="about-card">
          <view class="about-row about-check pressable" role="button" aria-label="关于我们" @click="goAbout">
            <Icon name="info" :size="30" color="#EF5A3C" />
            <view class="about-row__text">
              <text class="about-row__name">关于我们</text>
              <text class="about-row__desc">我们的初心、检查更新、隐私政策与反馈</text>
            </view>
            <text class="about-row__arrow">›</text>
          </view>
        </view>
      </view>

      <view v-if="userStore.isLoggedIn" class="logout-button pressable" :class="{ 'is-disabled': logoutLoading }" role="button" aria-label="退出登录" @click="askForLogout">
        {{ logoutLoading ? '正在退出…' : '退出登录' }}
      </view>
      <view v-if="userStore.isLoggedIn" class="delete-account pressable" :class="{ 'is-disabled': deleteLoading }" role="button" aria-label="永久注销账号" @click="askDeleteAccount">
        {{ deleteLoading ? '正在注销…' : '永久注销账号' }}
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
.theme-card { padding: 26rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.field-label { display: block; margin-top: 12rpx; color: var(--mrc-text-deep); font-size: 23rpx; font-weight: 700; }

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
.backend-card { padding: 24rpx 26rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.backend-current { display: flex; align-items: center; gap: 16rpx; padding-bottom: 20rpx; border-bottom: 2rpx solid var(--mrc-border-light); }
.backend-current__text { display: flex; min-width: 0; flex-direction: column; gap: 4rpx; }
.backend-current__name { color: var(--mrc-text-strong); font-size: 26rpx; font-weight: 800; }
.backend-current__url { overflow: hidden; color: var(--mrc-text-sub); font-size: 20rpx; text-overflow: ellipsis; white-space: nowrap; }
.backend-options { display: flex; flex-direction: column; padding-top: 8rpx; }
.backend-option { display: flex; min-height: 92rpx; align-items: center; justify-content: space-between; gap: 16rpx; border-bottom: 2rpx solid var(--mrc-border-light); }
.backend-option:last-child { border-bottom: 0; }
.backend-option.is-selected .backend-option__name { color: var(--mrc-accent); }
.backend-option__main { display: flex; min-width: 0; flex-direction: column; gap: 2rpx; }
.backend-option__name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 700; }
.backend-option__desc { color: var(--mrc-text-sub); font-size: 21rpx; }
.backend-option__check { color: var(--mrc-accent); font-size: 30rpx; font-weight: 800; }
.backend-note { display: block; padding-top: 16rpx; border-top: 2rpx solid var(--mrc-border-light); color: var(--mrc-text-light); font-size: 20rpx; line-height: 1.5; }
.about-card { padding: 8rpx 26rpx 6rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.about-row { display: flex; min-height: 100rpx; align-items: center; gap: 16rpx; border-bottom: 2rpx solid var(--mrc-border-light); }
.about-row:last-child { border-bottom: 0; }
.about-row__text { display: flex; min-width: 0; flex-direction: column; gap: 4rpx; }
.about-row__name { color: var(--mrc-text-strong); font-size: 27rpx; font-weight: 800; }
.about-row__desc { color: var(--mrc-text-sub); font-size: 21rpx; }
.about-check { justify-content: space-between; }
.about-row__arrow { margin-left: auto; color: var(--mrc-text-light); font-size: 32rpx; font-weight: 800; }
.logout-button { display: flex; min-height: 96rpx; align-items: center; justify-content: center; margin-top: 44rpx; border: 2rpx solid var(--mrc-color-danger-soft); border-radius: 48rpx; color: var(--mrc-color-danger); background: var(--mrc-surface); font-size: 28rpx; font-weight: 700; }
.delete-account { display: flex; min-height: 78rpx; align-items: center; justify-content: center; margin-top: 16rpx; color: var(--mrc-text-light); font-size: 23rpx; text-decoration: underline; }
.settings-footnote { display: block; padding: 18rpx 28rpx 0; color: var(--mrc-text-light); font-size: 21rpx; line-height: 1.55; text-align: center; }
.pressable { transition: transform 180ms ease, opacity 180ms ease; }
.pressable:active { transform: scale(.97); }
.is-disabled { opacity: .48; pointer-events: none; }
@media (max-width: 350px) { .settings-content { width: calc(100% - 32rpx); } .settings-intro__image { width: 190rpx; height: 190rpx; opacity: .76; } .theme-colors__list { grid-template-columns: repeat(2, 1fr); } }
@media (prefers-reduced-motion: reduce) { .pressable { transition: none; } .pressable:active { transform: none; } }
</style>
