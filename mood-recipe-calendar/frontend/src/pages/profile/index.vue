<script setup lang="ts">
import {computed, ref} from 'vue'
import {useNavBar} from '@/composables/useNavBar'
import {STATIC_BASE_URL} from '@/utils/assets'
import Icon from '../../components/common/Icon.vue'
import {useUserStore} from '../../stores/user'
import {refreshUserInfo, navigateToLogin} from '../../utils/login'
import {toast, toastError} from '../../utils/toast'
import SuccessModal from '../../components/guozai/SuccessModal.vue'
import {fetchShareStatus, type ShareStatus} from '@/api/share'
import {fetchCheckinStatus, doCheckin, type CheckinStatus} from '@/api/checkin'
import {fetchFridgeSummary, type FridgeSummary} from '@/api/fridge'
import {uploadFile} from '@/api/request'
import {updateUserInfo} from '@/api/auth'
import {chooseImageFile} from '@/utils/chooseImage'

definePage({
  name: 'profile',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的',
  },
})

const router = useRouter()
const userStore = useUserStore()
/** 转发给好友/群：必须定义 onShareAppMessage 胶囊「···」里的转发才可用 */
onShareAppMessage(() => {
  const sharer = userStore.openid
  const path = `/pages/profile/index${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ''}`
  return { title: '锅仔 · 按心情帮你决定今天吃什么', path }
})
/** 分享到朋友圈：必须定义 onShareTimeline 才可用 */
onShareTimeline(() => {
  const sharer = userStore.openid
  return {
    title: '锅仔 · 按心情帮你决定今天吃什么',
    query: sharer ? `sharer=${encodeURIComponent(sharer)}` : '',
  }
})
// 顶部用户信息卡适配状态栏 + 右侧齿轮避让胶囊
const nav = useNavBar()

const loading = ref(true)
const avatarUpdating = ref(false)
const showEdit = ref(false)
const editNickname = ref('')
const nicknameSaving = ref(false)
const shareStatus = ref<ShareStatus>({ isSharer: false, sharedToday: false })
const checkinStatus = ref<CheckinStatus | null>(null)
const checkingIn = ref(false)
const fridgeSummary = ref<FridgeSummary>()
const fridgeSummaryText = computed(() => {
  const s = fridgeSummary.value
  if (!s) return '记下食材，锅仔帮你看着'
  if (s.expired > 0) return `有 ${s.expired} 项已过期，请先处理`
  if (s.soon > 0) return `今天优先消耗 ${s.soon} 项食材`
  return `已记录 ${s.total} 项食材`
})
const fridgeHasAlert = computed(() => {
  const s = fridgeSummary.value
  return !!s && (s.expired > 0 || s.soon > 0)
})
const showCheckinModal = ref(false)

/** 「我的」页只加载账号域数据：记录域已迁至记录 tab，这里不再拉记录/统计 */
async function loadData() {
  loading.value = true
  userStore.restoreFromStorage()
  if (!userStore.isLoggedIn) {
    loading.value = false
    return
  }
  try {
    await refreshUserInfo()
    // 分享家徽章与签到状态：失败静默，不阻塞主流程
    fetchShareStatus().then(s => { shareStatus.value = s }).catch(() => {})
    fetchCheckinStatus().then(s => { checkinStatus.value = s }).catch(() => {})
    fetchFridgeSummary().then(s => { fridgeSummary.value = s }).catch(() => {})
  }
  catch (e: any) {
    toastError(e, '加载失败，请稍后重试')
  }
  finally {
    loading.value = false
  }
}

onShow(() => {
  loadData()
  achievementUnread.value = Number(uni.getStorageSync('mrc_achv_unread') || 0)
})

async function handleIdentityCard() {
  if (userStore.isLoggedIn) {
    goSettings()
    return
  }
  navigateToLogin()
}

function goReport() {
  router.push({ name: 'report' })
}
/** 成就未读数：记录解锁后累计，进形象馆即清零。 */
const achievementUnread = ref(0)
function goGallery() {
  router.push({ name: 'gallery' })
}
function goSettings() {
  router.push({ name: 'settings' })
}

/** 更换头像：选择图片 → 上传 COS → 更新用户信息 */
async function updateAvatar(filePath: string) {
  if (!userStore.isLoggedIn) {
    navigateToLogin()
    return
  }
  if (!filePath || avatarUpdating.value)
    return
  avatarUpdating.value = true
  try {
    const uploaded = await uploadFile(filePath, 'avatar')
    await updateUserInfo({ openid: userStore.openid, avatarUrl: uploaded.url })
    await refreshUserInfo()
    toastSuccess('头像已更新')
  }
  catch (e: any) {
    toastError(e, '头像更新失败，请重试')
  }
  finally {
    avatarUpdating.value = false
  }
}

function onChooseAvatar() {
  if (!userStore.isLoggedIn) {
    navigateToLogin()
    return
  }
  if (avatarUpdating.value) {
    toast('头像更新中，请稍候')
    return
  }
  chooseImageFile({
    onSelected: (filePath) => updateAvatar(filePath),
    onFail: () => toast('选择图片失败，请重试'),
  })
}

/** 打开编辑资料弹窗：可一键选用微信头像与微信昵称 */
function openEdit() {
  if (!userStore.isLoggedIn) {
    // 未登录时昵称位展示的文字就是「微信登录」，点击必须去登录页，不能死拦
    navigateToLogin()
    return
  }
  editNickname.value = userStore.userInfo?.nickname || ''
  showEdit.value = true
}

/** 微信头像选择回调：直接拿到微信头像临时文件并上传 */
function onChooseWechatAvatar(event: any) {
  updateAvatar(event.detail?.avatarUrl || '')
}

/** 弹窗内保存昵称 */
async function saveEditNickname() {
  const value = editNickname.value.trim()
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
    await refreshUserInfo(true)
    editNickname.value = userStore.userInfo?.nickname || value
    toastSuccess('昵称已保存')
  }
  catch (e: any) {
    toastError(e, '昵称保存失败，请重试')
  }
  finally {
    nicknameSaving.value = false
  }
}
function showPrivacy() {
  router.push({ name: 'privacy' })
}
function goAbout() {
  router.push({ name: 'about' })
}

function goFridge() {
  router.push({ name: 'fridge' })
}
function goFeedback() {
  router.push({ name: 'feedback' })
}

/** 「我的」页内联签到：签到随机领对话券或三餐券，弹窗确认后直达对应功能页。 */
const checkinGiftType = computed(() => checkinStatus.value?.giftType || 'CONVERSATION')
const checkinGiftTitle = computed(() => {
  if (!checkinStatus.value?.checkedIn) return '每日签到 · 领随机赠礼'
  return checkinGiftType.value === 'MEAL_PLAN' ? '今日已签到 · 三餐券 ×2' : '今日已签到 · 对话券 ×3'
})
const checkinGiftSub = computed(() => {
  if (!checkinStatus.value?.checkedIn) return '签到随机领对话券或三餐搭配券'
  return `已连续 ${checkinStatus.value.streak} 天，明天再来`
})
const checkinGiftBtn = computed(() => checkinGiftType.value === 'MEAL_PLAN' ? '看今日三餐' : '去和锅仔聊聊')
function onCheckinGiftConfirm() {
  showCheckinModal.value = false
  router.push({ name: checkinGiftType.value === 'MEAL_PLAN' ? 'daily-meal-plan' : 'meal-agent' })
}
async function quickCheckin() {
  if (!userStore.isLoggedIn) {
    navigateToLogin()
    return
  }
  if (checkingIn.value || checkinStatus.value?.checkedIn)
    return
  checkingIn.value = true
  try {
    await doCheckin()
    checkinStatus.value = await fetchCheckinStatus()
    showCheckinModal.value = true
  }
  catch (e: any) {
    toastError(e, '签到失败，请稍后再试')
  }
  finally {
    checkingIn.value = false
  }
}

</script>

<template>
  <view class="profile-page mrc-hero">
    <view
      class="profile-topbar"
      :style="{ paddingTop: `${nav.statusBarHeight}px`, minHeight: `${nav.navBarHeight}px`, paddingRight: `${nav.capsuleRightGap}px` }"
    >
      <text class="profile-topbar__title">
        我的
      </text>
      <view class="profile-topbar__settings" role="button" aria-label="打开设置" @click="goSettings">
        <wd-icon name="settings" size="24px" color="#EF5A3C" />
      </view>
    </view>

    <view class="profile-identity" role="button" :aria-label="userStore.isLoggedIn ? '打开设置修改个人资料' : '微信登录'" @click="handleIdentityCard">
      <view class="profile-identity__glow profile-identity__glow--one" />
      <view class="profile-identity__glow profile-identity__glow--two" />
      <image
        :src="userStore.isLoggedIn ? `${STATIC_BASE_URL}/static/guozai/action_08_peek.png` : `${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`"
        class="profile-identity__companion"
        mode="aspectFit"/>

      <view class="profile-identity__main">
        <view class="profile-avatar" :class="{ 'profile-avatar--logged': userStore.userInfo?.avatarUrl }" role="button" :aria-label="userStore.isLoggedIn ? '修改头像与昵称' : '登录后即可修改资料'" @click.stop="openEdit">
          <image
            class="profile-avatar__img"
            :src="userStore.isLoggedIn ? userStore.userInfo?.avatarUrl || `${STATIC_BASE_URL}/static/guozai/mood_01_happy.png` : `${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`"
            :mode="userStore.userInfo?.avatarUrl ? 'aspectFill' : 'aspectFit'"
          />
          <view v-if="userStore.isLoggedIn" class="profile-avatar__edit">
            <wd-icon name="settings" size="14px" color="#EF5A3C" />
          </view>
          <view v-if="avatarUpdating" class="profile-avatar__loading">
            上传中
          </view>
        </view>

        <view class="profile-userinfo">
          <text class="profile-userinfo__eyebrow">
            {{ userStore.isLoggedIn ? '锅仔的小饭友' : '锅仔在这里等你' }}
          </text>
          <view class="profile-name-wrap" role="button" :aria-label="userStore.isLoggedIn ? '修改昵称' : '登录后即可修改昵称'" @click.stop="openEdit">
            <text class="profile-name">
              {{ userStore.isLoggedIn ? userStore.userInfo?.nickname || '给自己取个昵称' : '微信登录' }}
            </text>
            <text class="profile-name__arrow">
              ›
            </text>
          </view>
          <text class="profile-login-hint">
            {{ userStore.isLoggedIn ? '点击进入设置，修改头像和昵称' : '登录后让锅仔慢慢记住你的口味' }}
          </text>
        </view>
      </view>

      <view class="profile-identity__footer">
        <view class="profile-login-state">
          <view class="profile-login-state__dot" :class="{ 'profile-login-state__dot--online': userStore.isLoggedIn }" />
          <text>{{ userStore.isLoggedIn ? '微信身份已连接' : '点击即可微信登录' }}</text>
        </view>
        <text class="profile-identity__promise">
          今天也要好好吃饭
        </text>
      </view>
    </view>

    <view class="profile-section-head">
      <view>
        <text class="profile-section-head__eyebrow">
          锅仔陪你
        </text><text class="profile-section-head__title">
          更懂你的每一餐
        </text>
      </view>
      <text class="profile-section-head__sub">
        慢慢记录，慢慢熟悉
      </text>
    </view>

    <!-- 功能行：统一行卡系统（同圆角/同内结构/同右端），渐变只留给顶部身份卡 -->
    <view class="profile-row" :class="{ 'profile-row--alert': fridgeHasAlert }" role="button" aria-label="打开我的冰箱" @click="goFridge">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_07_empty.png`" class="profile-row__img" mode="aspectFit"/>
      <view class="profile-row__main">
        <text class="profile-row__eyebrow">锅仔的新记忆</text>
        <text class="profile-row__title">我的冰箱</text>
        <text class="profile-row__sub">{{ fridgeSummaryText }}</text>
      </view>
      <view class="profile-row__arrow">›</view>
    </view>

    <!-- 锅仔形象馆入口：成就墙 + 表情包 + 锅仔养成 -->
    <view class="profile-row" role="button" aria-label="打开锅仔形象馆" @click="goGallery">
      <image :src="`${STATIC_BASE_URL}/static/guozai/mood_06_hungry.png`" class="profile-row__img" mode="aspectFit"/>
      <view class="profile-row__main">
        <text class="profile-row__eyebrow">锅仔的收藏</text>
        <view class="profile-row__title-row">
          <text class="profile-row__title">锅仔形象馆</text>
          <text v-if="shareStatus.isSharer" class="profile-row__tag profile-row__tag--sharer">分享家</text>
        </view>
        <text class="profile-row__sub">{{ achievementUnread > 0 ? `有 ${achievementUnread} 个新成就，去点亮` : '成就徽章 · 表情包 · 锅仔养成' }}</text>
      </view>
      <view class="profile-row__arrow">
        <text v-if="achievementUnread > 0" class="profile-row__dot">{{ achievementUnread }}</text>›
      </view>
    </view>

    <!-- 内联签到：非会员签到随机领对话券或三餐券，留在「我的」页一步完成 -->
    <view
      class="profile-row profile-row--action"
      role="button"
      :aria-label="checkinStatus?.checkedIn ? '今日已签到' : '签到领取今日随机赠礼'"
      @click="quickCheckin"
    >
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_13_wave.png`" class="profile-row__img" mode="aspectFit"/>
      <view class="profile-row__main">
        <text class="profile-row__eyebrow">每日签到</text>
        <text class="profile-row__title">{{ checkinGiftTitle }}</text>
        <text class="profile-row__sub">{{ checkinGiftSub }}</text>
      </view>
      <view
        class="profile-row__btn"
        :class="{ 'profile-row__btn--done': checkinStatus?.checkedIn }"
      >
        {{ checkinStatus?.checkedIn ? '已签' : (checkingIn ? '签到中' : '签到') }}
      </view>
    </view>

    <!-- 签到成功弹窗：按今日随机赠礼类型定制，确认即直达使用页 -->
    <SuccessModal
      :visible="showCheckinModal"
      title="签到成功！"
      :subtitle="checkinGiftType === 'MEAL_PLAN'
        ? `今日赠礼：三餐搭配券 ×2，已连续签到 ${checkinStatus?.streak ?? 1} 天`
        : `今日赠礼：锅仔对话 ×3，已连续签到 ${checkinStatus?.streak ?? 1} 天`"
      :confirm-text="checkinGiftBtn"
      @confirm="onCheckinGiftConfirm"
    />

    <!-- 年度报告：同一行卡系统，不再是缩一圈的小格 -->
    <view class="profile-row" role="button" aria-label="打开我的年度报告" @click="goReport">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`" class="profile-row__img" mode="aspectFit"/>
      <view class="profile-row__main">
        <text class="profile-row__eyebrow">年度回顾</text>
        <text class="profile-row__title">年度报告</text>
        <text class="profile-row__sub">看看这一年的味道</text>
      </view>
      <view class="profile-row__arrow">›</view>
    </view>

    <!-- 底部链接 -->
    <view class="profile-footer">
      <text class="profile-footer__link" role="button" @click="showPrivacy">
        隐私政策
      </text>
      <view class="profile-footer__dot" />
      <text class="profile-footer__link" role="button" @click="goFeedback">
        反馈建议
      </text>
      <view class="profile-footer__dot" />
      <text class="profile-footer__link" role="button" @click="goAbout">
        关于我们
      </text>
      <image :src="`${STATIC_BASE_URL}/static/guozai/mood_01_happy.png`" class="profile-footer__guozai"
             mode="aspectFit"/>
    </view>

    <!-- 编辑资料弹窗：一键选用微信头像与微信昵称；z-index 须高于 tabbar(wd-tabbar 默认 10)，
         否则同层级下 DOM 靠后的 tabbar 会盖住弹窗底部按钮 -->
    <wd-popup v-model="showEdit" position="bottom" :z-index="30" :close-on-click-modal="true" :safe-area-inset-bottom="true"
      custom-style="border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);">
      <view class="edit-sheet">
        <view class="edit-sheet__head">
          <text class="edit-sheet__title">编辑资料</text>
          <view class="edit-sheet__close" role="button" aria-label="关闭" @click="showEdit = false">
            <wd-icon name="close" size="22px" color="#A1826A" />
          </view>
        </view>

        <view class="edit-sheet__row">
          <text class="edit-sheet__label">头像</text>
          <view class="edit-sheet__avatar">
            <image :src="userStore.userInfo?.avatarUrl || `${STATIC_BASE_URL}/static/guozai/mood_01_happy.png`"
                   class="edit-sheet__avatar-img" mode="aspectFill" />
            <view v-if="avatarUpdating" class="edit-sheet__avatar-loading">上传中</view>
          </view>
        </view>
        <view class="edit-sheet__actions">
          <!-- #ifdef MP-WEIXIN -->
          <button class="edit-sheet__btn edit-sheet__btn--primary" open-type="chooseAvatar"
                  :disabled="avatarUpdating" @chooseavatar="onChooseWechatAvatar">
            使用微信头像
          </button>
          <!-- #endif -->
          <view class="edit-sheet__btn" role="button" aria-label="从相册选择头像" @click="onChooseAvatar">
            从相册选择
          </view>
        </view>

        <view class="edit-sheet__row edit-sheet__row--column">
          <text class="edit-sheet__label">昵称</text>
          <!-- #ifdef MP-WEIXIN -->
          <input v-model="editNickname" class="edit-sheet__input" type="nickname" :maxlength="24"
                 placeholder="点击选用微信昵称或输入" confirm-type="done" @confirm="saveEditNickname" />
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <input v-model="editNickname" class="edit-sheet__input" type="text" :maxlength="24"
                 placeholder="给自己取个昵称" confirm-type="done" @confirm="saveEditNickname" />
          <!-- #endif -->
        </view>
        <view class="edit-sheet__save pressable"
              :class="{ 'is-disabled': nicknameSaving || !editNickname.trim() || editNickname.trim() === userStore.userInfo?.nickname }"
              role="button" aria-label="保存昵称" @click="saveEditNickname">
          {{ nicknameSaving ? '保存中…' : '保存昵称' }}
        </view>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  padding: 0 32rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部标题栏：右侧为微信胶囊留位 */
.profile-topbar { display: flex; align-items: center; justify-content: space-between; box-sizing: content-box; }
.profile-topbar__title { color: var(--mrc-text-strong); font-size: 42rpx; font-weight: var(--mrc-fw-heavy); }
.profile-topbar__settings { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 50%; background: var(--mrc-surface-peach); box-shadow: var(--mrc-shadow-sm); }
.profile-topbar__settings:active { transform: scale(.94); }


/* 身份卡 */
.profile-identity { position: relative; overflow: hidden; margin: 8rpx 0 24rpx; padding: 34rpx 30rpx 24rpx; border: 2rpx solid var(--mrc-border); border-radius: 40rpx; background: linear-gradient(145deg, var(--mrc-surface) 0%, var(--mrc-surface-peach) 58%, var(--mrc-surface-sun) 100%); box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss); }
.profile-identity__glow { position: absolute; border-radius: 50%; pointer-events: none; }
.profile-identity__glow--one { width: 250rpx; height: 250rpx; right: -90rpx; top: -120rpx; background: rgba(255, 197, 61, .18); }
.profile-identity__glow--two { width: 180rpx; height: 180rpx; left: -100rpx; bottom: -120rpx; background: rgba(255, 107, 91, .09); }
.profile-identity__companion { position: absolute; right: 10rpx; bottom: 40rpx; width: 132rpx; height: 132rpx; opacity: .9; pointer-events: none; }
.profile-identity__main { position: relative; z-index: 1; display: flex; align-items: center; gap: 26rpx; padding-right: 76rpx; }
.profile-userinfo { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 7rpx; }
.profile-userinfo__eyebrow { color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; letter-spacing: 2rpx; }
.profile-avatar { position: relative; width: 128rpx; height: 128rpx; display: flex; flex-shrink: 0; align-items: center; justify-content: center; overflow: visible; border: 6rpx solid rgba(255, 255, 255, .86); border-radius: 50%; background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface-sun)); box-shadow: 0 10rpx 24rpx rgba(148, 91, 56, .16); }
.profile-avatar--logged { background: var(--mrc-surface-peach); }
.profile-avatar__img { width: 118rpx; height: 118rpx; border-radius: 50%; }
.profile-avatar__edit { position: absolute; right: -4rpx; bottom: -4rpx; width: 50rpx; height: 50rpx; display: flex; align-items: center; justify-content: center; border: 3rpx solid var(--mrc-surface); border-radius: 50%; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); }
.profile-avatar__loading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; border-radius: 50%; color: #fff; background: rgba(40, 24, 16, .55); font-size: 20rpx; font-weight: 700; }
.profile-name-wrap { display: flex; min-height: 88rpx; align-items: center; gap: 4rpx; }
.profile-name { max-width: 360rpx; overflow: hidden; color: var(--mrc-text-strong); font-size: 40rpx; font-weight: var(--mrc-fw-heavy); line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.profile-name__arrow { color: var(--mrc-text-light); font-size: 34rpx; line-height: 1; }
.profile-login-hint { color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 500; line-height: 1.4; }
.profile-identity__footer { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-top: 26rpx; padding-top: 20rpx; border-top: 2rpx solid rgba(226, 189, 152, .55); }
.profile-login-state { display: flex; align-items: center; gap: 10rpx; color: var(--mrc-text-sub); font-size: 21rpx; font-weight: 600; }
.profile-login-state__dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--mrc-text-light); }
.profile-login-state__dot--online { background: var(--mrc-mint); box-shadow: 0 0 0 6rpx rgba(40, 194, 160, .12); }
.profile-identity__promise { padding-right: 92rpx; color: var(--mrc-accent); font-size: 21rpx; font-weight: 700; }

.profile-section-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20rpx; margin: 0 4rpx 18rpx; }
.profile-section-head__eyebrow, .profile-section-head__title { display: block; }
.profile-section-head__eyebrow { margin-bottom: 6rpx; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.profile-section-head__title { color: var(--mrc-text-strong); font-size: 32rpx; font-weight: var(--mrc-fw-heavy); }
.profile-section-head__sub { padding-bottom: 2rpx; color: var(--mrc-text-sub); font-size: 20rpx; }

/* ============ 功能行卡系统（「我的」页列表统一语言） ============
   同圆角/同内边距/同最低高度/同右端件；整卡近白暖面，色彩交给左侧锅仔图，
   渐变只留给顶部身份卡——这是本页从「四种卡片语言」收拢为一种的关键。 */
.profile-row {
  display: flex;
  min-height: 144rpx;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 28rpx;
  box-sizing: border-box;
  margin-bottom: 16rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
  transition: transform 0.15s ease;
}
.profile-row:active { transform: scale(.98); }
/* 临期预警态：整卡换暖阳底，是本系统里唯一的整卡变色（语义需要，不是装饰） */
.profile-row--alert { background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); border-color: var(--mrc-border); }
.profile-row__img { width: 88rpx; height: 88rpx; flex-shrink: 0; }
.profile-row__main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 5rpx; }
.profile-row__eyebrow { color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.profile-row__title-row { display: flex; align-items: center; gap: 12rpx; min-width: 0; }
.profile-row__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.profile-row__sub { color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.4; }
/* 标题旁小徽章（分享家等）：从右侧堆叠改为内联，行高立刻齐整 */
.profile-row__tag {
  flex-shrink: 0;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  font-size: 18rpx;
  font-weight: 700;
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
}
.profile-row__tag--sharer { background: rgba(26, 173, 25, .12); color: #1AAD19; }
/* 右端：箭头（未读红点挂在箭头上）或动作按钮，二选一 */
.profile-row__arrow { position: relative; flex-shrink: 0; color: var(--mrc-text-light); font-size: 40rpx; font-weight: 300; line-height: 1; }
.profile-row__dot {
  position: absolute;
  top: -18rpx;
  left: -26rpx;
  min-width: 34rpx;
  height: 34rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: var(--mrc-accent);
  color: #fff;
  font-size: 20rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 10rpx rgba(255, 107, 91, 0.4);
}
.profile-row__btn { flex-shrink: 0; padding: 16rpx 34rpx; border-radius: 999rpx; background: var(--mrc-accent); color: #fff; font-size: 26rpx; font-weight: 800; box-shadow: var(--mrc-shadow-sm); }
.profile-row__btn--done { background: rgba(148, 91, 56, .16); color: var(--mrc-text-sub); box-shadow: none; }

/* 底部 */
.profile-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  min-height: 112rpx;
  padding: 8rpx 47rpx;
  position: relative;
}
.profile-footer__link {
  min-height: 88rpx;
  display: flex;
  align-items: center;
  font-size: 23rpx;
  color: var(--mrc-text-sub);
}
.profile-footer__dot { width: 5rpx; height: 5rpx; flex-shrink: 0; border-radius: 50%; background: var(--mrc-border-strong); }
.profile-footer__contact { margin: 0; padding: 0; line-height: inherit; background: transparent; border: 0; }
.profile-footer__contact::after { border: 0; }
.profile-footer__guozai {
  position: absolute;
  right: 0;
  bottom: -8rpx;
  width: 100rpx;
  height: 100rpx;
}

/* 编辑资料弹窗：一键选用微信头像与微信昵称 */
.edit-sheet { padding: 28rpx 30rpx 36rpx; box-sizing: border-box; }
.edit-sheet__head { position: relative; display: flex; min-height: 56rpx; align-items: center; justify-content: center; margin-bottom: 22rpx; }
.edit-sheet__title { color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 800; }
.edit-sheet__close { position: absolute; right: 0; top: 50%; transform: translateY(-50%); display: flex; width: 56rpx; height: 56rpx; align-items: center; justify-content: center; }
.edit-sheet__row { display: flex; align-items: center; justify-content: space-between; min-height: 120rpx; padding: 12rpx 4rpx; border-bottom: 2rpx solid var(--mrc-border-light); }
.edit-sheet__row--column { flex-direction: column; align-items: stretch; gap: 14rpx; }
.edit-sheet__label { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.edit-sheet__avatar { position: relative; display: flex; width: 112rpx; height: 112rpx; align-items: center; justify-content: center; overflow: hidden; border: 4rpx solid var(--mrc-surface-peach); border-radius: 50%; background: var(--mrc-surface-peach); box-shadow: var(--mrc-shadow-sm); }
.edit-sheet__avatar-img { width: 104rpx; height: 104rpx; border-radius: 50%; }
.edit-sheet__avatar-loading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; border-radius: 50%; color: #fff; background: rgba(40, 24, 16, .62); font-size: 20rpx; font-weight: 700; }
.edit-sheet__actions { display: flex; gap: 18rpx; margin: 22rpx 0 8rpx; }
.edit-sheet__btn { flex: 1; display: flex; align-items: center; justify-content: center; min-height: 92rpx; box-sizing: border-box; border-radius: 46rpx; color: var(--mrc-text-deep); background: var(--mrc-surface-2); font-size: 27rpx; font-weight: 700; }
.edit-sheet__btn--primary { color: #fff; background: var(--mrc-primary-grad, #EF5A3C); }
button.edit-sheet__btn--primary { margin: 0; padding: 0; line-height: normal; border: 0; }
button.edit-sheet__btn--primary::after { border: 0; }
.edit-sheet__input { height: 84rpx; box-sizing: border-box; padding: 0 22rpx; border: 2rpx solid var(--mrc-border); border-radius: 20rpx; color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 700; background: var(--mrc-surface); }
.edit-sheet__save { display: flex; min-height: 96rpx; align-items: center; justify-content: center; margin-top: 26rpx; border-radius: 48rpx; color: #fff; background: var(--mrc-primary-grad, #EF5A3C); font-size: 29rpx; font-weight: 800; }
.is-disabled { opacity: .48; pointer-events: none; }
.pressable { transition: transform 180ms ease, opacity 180ms ease; }
.pressable:active { transform: scale(.97); }
@media (prefers-reduced-motion: reduce) { .pressable { transition: none; } .pressable:active { transform: none; } }
</style>
