<script setup lang="ts">
import { ref } from 'vue'
import { STATIC_BASE_URL } from '@/utils/assets'
import { useUserStore } from '@/stores/user'
import { cancelAuthRecovery, consumeAuthReturn } from '@/utils/authRecovery'
import { ensureLogin } from '@/utils/login'
import { toastError } from '@/utils/toast'

definePage({ name: 'login', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '微信登录' } })

const router = useRouter()
const userStore = useUserStore()
const loggingIn = ref(false)
const canWechatLogin = ref(false)

// #ifdef MP-WEIXIN
canWechatLogin.value = true
// #endif

async function login() {
  if (!canWechatLogin.value || loggingIn.value)
    return
  loggingIn.value = true
  try {
    userStore.clearLogoutFlag()
    await ensureLogin()
    const target = consumeAuthReturn()
    if (target)
      uni.reLaunch({ url: target })
    else
      router.pushTab({ name: 'profile' })
  }
  catch (error) {
    toastError(error, '登录失败，请重试')
  }
  finally {
    loggingIn.value = false
  }
}

function backToProfile() {
  cancelAuthRecovery()
  router.pushTab({ name: 'profile' })
}
</script>

<template>
  <view class="login-page">
    <view class="login-orb login-orb--one" />
    <view class="login-orb login-orb--two" />

    <view class="login-hero">
      <image :src="STATIC_BASE_URL + '/static/guozai/action_09_celebrate.png'" mode="aspectFit" aria-label="欢迎你的锅仔" />
      <text class="login-kicker">
        锅仔在这里等你
      </text>
      <text class="login-title">
        一起把每一顿，<br>过成自己的小日子
      </text>
      <text class="login-copy">
        登录后，锅仔才能记住你的口味、备餐计划和每一次认真吃饭的时刻。
      </text>
    </view>

    <view class="login-card">
      <!-- #ifdef MP-WEIXIN -->
      <text class="login-card__title">
        连接微信身份
      </text>
      <text class="login-card__copy">
        仅用于保存你的个人菜谱、偏好与记录。
      </text>
      <button class="login-button" :disabled="loggingIn" @click="login">
        {{ loggingIn ? '锅仔正在连接微信…' : '微信一键登录' }}
      </button>
      <!-- #endif -->

      <!-- #ifndef MP-WEIXIN -->
      <text class="login-card__title">
        请在微信小程序中登录
      </text>
      <text class="login-card__copy">
        微信身份登录仅在小程序内可用。打开小程序后，锅仔会继续陪你安排每一顿饭。
      </text>
      <view class="back-button" role="button" @click="backToProfile">
        先逛逛 ›
      </view>
      <!-- #endif -->

      <text class="login-privacy">
        登录即表示你同意我们按隐私政策保护你的资料。
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.login-page { position: relative; min-height: 100vh; overflow: hidden; padding: calc(88rpx + env(safe-area-inset-top)) 48rpx calc(64rpx + env(safe-area-inset-bottom)); box-sizing: border-box; background: var(--mrc-bg); }.login-orb { position: absolute; border-radius: 50%; filter: blur(4rpx); opacity: .65; pointer-events: none; }.login-orb--one { top: 6%; right: -100rpx; width: 340rpx; height: 340rpx; background: var(--mrc-surface-peach); }.login-orb--two { bottom: 12%; left: -150rpx; width: 360rpx; height: 360rpx; background: var(--mrc-surface-sun); }.login-hero, .login-card { position: relative; z-index: 1; }.login-hero { display: flex; flex-direction: column; align-items: center; text-align: center; }.login-hero image { width: 320rpx; height: 320rpx; }.login-kicker { margin-top: 10rpx; color: var(--mrc-accent); font-size: 22rpx; font-weight: 800; letter-spacing: 2rpx; }.login-title { margin-top: 18rpx; color: var(--mrc-text-strong); font-size: 45rpx; font-weight: 800; line-height: 1.34; }.login-copy { margin-top: 18rpx; color: var(--mrc-text-sub); font-size: 25rpx; line-height: 1.7; }.login-card { margin-top: 52rpx; padding: 32rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }.login-card__title { display: block; color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; }.login-card__copy { display: block; margin-top: 10rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.6; }.login-button, .back-button { width: 100%; min-height: 96rpx; display: flex; align-items: center; justify-content: center; margin-top: 28rpx; border: 0; border-radius: 48rpx; box-sizing: border-box; background: var(--mrc-primary-grad); color: #fff; font-size: 29rpx; font-weight: 800; }.login-button::after { border: 0; }.back-button { background: var(--mrc-surface-peach); color: var(--mrc-accent); }.login-privacy { display: block; margin-top: 24rpx; color: var(--mrc-text-light); font-size: 20rpx; line-height: 1.5; text-align: center; }.login-button:active, .back-button:active { transform: scale(.98); opacity: .86; } .login-button, .back-button { transition: transform 160ms ease-out, opacity 160ms ease-out; } @media (prefers-reduced-motion: reduce) { .login-button, .back-button { transition: none; } }
</style>
