<script setup lang="ts">
import type {RecordItem} from '../../api/records'
import {fetchRecords, fetchStats} from '../../api/records'
import {ref} from 'vue'
import {useNavBar} from '@/composables/useNavBar'
import {STATIC_BASE_URL} from '@/utils/assets'
import Icon from '../../components/common/Icon.vue'
import {useUserStore} from '../../stores/user'
import {refreshUserInfo} from '../../utils/login'
import {toast, toastError, toastSuccess} from '../../utils/toast'
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
// 顶部用户信息卡适配状态栏 + 右侧齿轮避让胶囊
const nav = useNavBar()

const loading = ref(true)
const avatarUpdating = ref(false)
const stats = ref({ totalRecords: 0, totalDays: 0, currentStreak: 0, topDishes: [] as { name: string, count: number }[] })
const history = ref<RecordItem[]>([])

const MOOD_IMG_MAP: Record<string, string> = {
  开心: `${STATIC_BASE_URL}
/static/guozai/mood_01_happy.png`,
  平静: `${STATIC_BASE_URL}/static/guozai/mood_02_calm.png`,
  疲惫: `${STATIC_BASE_URL}/static/guozai/mood_03_tired.png`,
  焦虑: `${STATIC_BASE_URL}/static/guozai/mood_04_anxious.png`,
  难过: `${STATIC_BASE_URL}/static/guozai/mood_05_sad.png`,
  嘴馋: `${STATIC_BASE_URL}/static/guozai/mood_06_hungry.png`,
  低落: `${STATIC_BASE_URL}/static/guozai/mood_07_low.png`,
  想家: `${STATIC_BASE_URL}/static/guozai/mood_08_homesick.png`,
}

async function loadData() {
  loading.value = true
  userStore.restoreFromStorage()
  if (!userStore.isLoggedIn) {
    stats.value = { totalRecords: 0, totalDays: 0, currentStreak: 0, topDishes: [] }
    history.value = []
    loading.value = false
    return
  }
  try {
    const openid = userStore.openid
    await refreshUserInfo()
    const [statsData, records] = await Promise.all([
      fetchStats(openid),
      fetchRecords(openid),
    ])
    stats.value = statsData
    history.value = records.slice(0, 3)
  }
  catch (e: any) {
    stats.value = { totalRecords: 0, totalDays: 0, currentStreak: 0, topDishes: [] }
    history.value = []
    toastError(e, '加载失败，请稍后重试')
  }
  finally {
    loading.value = false
  }
}

onShow(() => {
  loadData()
})

async function handleIdentityCard() {
  if (userStore.isLoggedIn) {
    goSettings()
    return
  }
  router.push({ name: 'login' })
}

function goReport() {
  router.push({ name: 'report' })
}
function goGallery() {
  router.push({ name: 'gallery' })
}
function goSettings() {
  router.push({ name: 'settings' })
}

/** 更换头像：选择图片 → 上传 COS → 更新用户信息 */
async function updateAvatar(filePath: string) {
  if (!userStore.isLoggedIn) {
    toastError(null, '请先登录再修改头像')
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
    toastError(null, '请先登录再修改头像')
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
function showPrivacy() {
  router.push({ name: 'privacy' })
}
function goAbout() {
  router.push({ name: 'about' })
}
function goTimeline() {
  router.push({ name: 'timeline' })
}

function openHistoryRecord(item: RecordItem) {
  uni.setStorageSync('mrc_timeline_record_id', item.id)
  goTimeline()
}
function goPreferences() {
  router.push({ name: 'preferences' })
}
function goWeeklyPlan() {
  router.push({ name: 'meal-agent' })
}
function goFeedback() {
  router.push({ name: 'feedback' })
}

function openStat(type: 'records' | 'days' | 'streak') {
  if (!userStore.isLoggedIn) {
    router.push({name: 'login'})
    return
  }
  // #ifdef MP-WEIXIN
  try {
    uni.vibrateShort({type: 'light'})
  } catch {
  }
  // #endif
  router.push({name: type === 'records' ? 'timeline' : 'calendar'})
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
        <view class="profile-avatar" :class="{ 'profile-avatar--logged': userStore.userInfo?.avatarUrl }" role="button" :aria-label="userStore.isLoggedIn ? '更换头像' : '登录后即可更换头像'" @click="onChooseAvatar">
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
          <view class="profile-name-wrap">
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

    <!-- 统计卡 -->
    <view class="profile-stats" aria-label="我的饮食记录统计">
      <view aria-label="查看全部饮食记录" class="profile-stats__item" role="button" @click="openStat('records')">
        <text class="profile-stats__label">
          总记录
        </text>
        <text class="profile-stats__value">
          {{ stats.totalRecords }}<text class="profile-stats__unit">
            条
          </text>
        </text>
        <text class="profile-stats__link">
          去翻看 ›
        </text>
      </view>
      <view class="profile-stats__divider" />
      <view aria-label="按日历查看记录天数" class="profile-stats__item" role="button" @click="openStat('days')">
        <text class="profile-stats__label">
          记录天数
        </text>
        <text class="profile-stats__value">
          {{ stats.totalDays }}<text class="profile-stats__unit">
            天
          </text>
        </text>
        <text class="profile-stats__link">
          看日历 ›
        </text>
      </view>
      <view class="profile-stats__divider" />
      <view aria-label="查看连续打卡记录" class="profile-stats__item" role="button" @click="openStat('streak')">
        <text class="profile-stats__label">
          连续打卡
        </text>
        <text class="profile-stats__value">
          {{ stats.currentStreak }}<text class="profile-stats__unit">
            天
          </text>
        </text>
        <text class="profile-stats__link">
          看足迹 ›
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

    <view class="profile-memory" role="button" aria-label="打开我的口味与忌口" @click="goPreferences">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_10_thinking.png`" class="profile-memory__guozai"
             mode="aspectFit"/>
      <view class="profile-memory__main">
        <text class="profile-memory__eyebrow">
          锅仔会一直记得
        </text>
        <text class="profile-memory__title">
          我的口味与忌口
        </text>
        <text class="profile-memory__sub">
          喜欢什么 · 葱和香菜 · 辣度 · 过敏食材
        </text>
      </view>
      <text class="profile-memory__arrow">
        ›
      </text>
    </view>

    <view class="profile-weekly-plan" role="button" aria-label="让锅仔安排这一周晚餐" @click="goWeeklyPlan">
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" class="profile-weekly-plan__guozai"
             mode="aspectFit"/>
      <view class="profile-weekly-plan__main">
        <text class="profile-weekly-plan__title">
          锅仔管饭
        </text>
        <text class="profile-weekly-plan__sub">
          记得口味 · 安排一周 · 随时帮你调整
        </text>
      </view>
      <text class="profile-weekly-plan__arrow">
        ›
      </text>
    </view>

    <!-- 锅仔形象馆入口（核心变现模块） -->
    <view class="profile-gallery" role="button" aria-label="打开锅仔形象馆" @click="goGallery">
      <image :src="`${STATIC_BASE_URL}/static/guozai/mood_06_hungry.png`" class="profile-gallery__guozai"
             mode="aspectFit"/>
      <view class="profile-gallery__main">
        <text class="profile-gallery__title">
          锅仔形象馆
        </text>
        <text class="profile-gallery__sub">
          表情包 · 主题素材 · 每日收藏
        </text>
      </view>
      <view class="profile-gallery__badge">
        每日签到
      </view>
      <text class="profile-gallery__arrow">
        ›
      </text>
    </view>

    <!-- 功能按钮 -->
    <view class="profile-actions">
      <view class="profile-action" role="button" aria-label="打开我的年度报告" @click="goReport">
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_09_celebrate.png`" class="profile-action__guozai"
               mode="aspectFit"/>
        <view class="profile-action__copy">
          <text class="profile-action__text">
            年度报告
          </text><text class="profile-action__sub">
            看看这一年的味道
          </text>
        </view>
      </view>
      <view class="profile-action" role="button" aria-label="打开菜谱时光机" @click="goTimeline">
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" class="profile-action__guozai"
               mode="aspectFit"/>
        <view class="profile-action__copy">
          <text class="profile-action__text">
            菜谱时光机
          </text><text class="profile-action__sub">
            往回翻每一顿饭
          </text>
        </view>
      </view>
    </view>

    <!-- 历史记录列表 -->
    <view class="profile-history">
      <view class="profile-history__head" role="button" aria-label="查看全部菜谱记录" @click="goTimeline">
        <view>
          <text class="profile-history__eyebrow">
            RECENT TABLE
          </text>
          <text class="profile-history__month">
            最近的食光
          </text>
        </view>
        <view class="profile-history__all">
          <text>{{ stats.totalRecords }} 顿</text>
          <text>查看全部 ›</text>
        </view>
      </view>
      <view v-if="history.length === 0" class="profile-history__empty">
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" mode="aspectFit"/>
        <view>
          <text class="profile-history__empty-title">
            第一顿饭，等你来记
          </text><text class="profile-history__empty-sub">
            记录后，锅仔会把它收进时光机
          </text>
        </view>
      </view>
      <view
        v-for="(item, i) in history"
        :key="item.id"
        class="profile-history__item"
        :aria-label="`查看 ${item.recordDate} 的${item.dishName}记录`"
        :class="{ 'profile-history__item--featured': i === 0 }"
        role="button"
        @click="openHistoryRecord(item)"
      >
        <view class="profile-history__photo">
          <image :aria-label="item.dishName" :src="item.imageUrl" lazy-load mode="aspectFill"/>
          <text class="profile-history__date">
            {{ item.recordDate?.slice(5) }}
          </text>
        </view>
        <view class="profile-history__main">
          <view class="profile-history__title-row">
            <text class="profile-history__dish">
              {{ item.dishName }}
            </text>
            <image :aria-label="`${item.moodTag}心情`"
                   :src="MOOD_IMG_MAP[item.moodTag] || `${STATIC_BASE_URL}/static/guozai/mood_01_happy.png`"
                   class="profile-history__mood" mode="aspectFit"/>
          </view>
          <text class="profile-history__meta">
            {{ item.moodTag }} · {{ item.cookingTime || 30 }} 分钟
          </text>
          <text v-if="item.note" class="profile-history__note">
            {{ item.note }}
          </text>
        </view>
        <text class="profile-history__arrow">
          ›
        </text>
      </view>
      <view v-if="history.length" class="profile-history__companion">
        <image :src="`${STATIC_BASE_URL}/static/guozai/action_08_peek.png`" aria-hidden="true" mode="aspectFit"/>
        <text>最近三顿已经收好，更多回忆在时光机里。</text>
      </view>
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
.profile-weekly-plan { display:flex; min-height:138rpx; align-items:center; gap:18rpx; margin-bottom:16rpx; padding:22rpx 26rpx; box-sizing:border-box; border:2rpx solid var(--mrc-border); border-radius:30rpx; background:linear-gradient(135deg,var(--mrc-surface-sun),var(--mrc-surface-peach)); box-shadow:var(--mrc-shadow-soft),var(--mrc-gloss); }
.profile-weekly-plan:active { transform: scale(.98); }
.profile-weekly-plan__guozai { width:88rpx; height:88rpx; flex-shrink:0; }
.profile-weekly-plan__main { flex:1; min-width:0; display:flex; flex-direction:column; gap:6rpx; }
.profile-weekly-plan__title { color:var(--mrc-text-deep); font-size:32rpx; font-weight:var(--mrc-fw-heavy); }
.profile-weekly-plan__sub { color:var(--mrc-text-sub); font-size:22rpx; line-height:1.45; }
.profile-weekly-plan__arrow { color:var(--mrc-text-light); font-size:40rpx; }

/* 统计卡 */
.profile-stats {
  display: flex;
  align-items: center;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 30rpx;
  padding: 26rpx 0;
  margin-bottom: 36rpx;
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
}
.profile-stats__item {
  flex: 1;
  min-height: 112rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9rpx;
  border-radius: 24rpx;
  transition: background-color 180ms ease-out, transform 180ms ease-out, opacity 180ms ease-out;
}

.profile-stats__item:active {
  background: var(--mrc-surface-peach);
  transform: scale(.97);
  opacity: .82;
}
.profile-stats__label {
  font-size: 22rpx;
  color: var(--mrc-text-sub);
}
.profile-stats__value {
  font-size: 46rpx;
  font-weight: var(--mrc-fw-heavy);
  color: var(--mrc-text-deep);
  line-height: 1;
}
.profile-stats__unit {
  font-size: 23rpx;
  font-weight: 600;
  margin-left: 4rpx;
}

.profile-stats__link {
  color: var(--mrc-accent);
  font-size: 18rpx;
  font-weight: 700;
}
.profile-stats__divider {
  width: 2rpx;
  height: 64rpx;
  background: var(--mrc-border-light);
}

@media (max-width: 350px) {
  .profile-stats__link {
    font-size: 16rpx;
  }
}

@media (prefers-reduced-motion: reduce) {
  .profile-stats__item {
    transition: none;
  }
}

.profile-section-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20rpx; margin: 0 4rpx 18rpx; }
.profile-section-head__eyebrow, .profile-section-head__title { display: block; }
.profile-section-head__eyebrow { margin-bottom: 6rpx; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.profile-section-head__title { color: var(--mrc-text-strong); font-size: 32rpx; font-weight: var(--mrc-fw-heavy); }
.profile-section-head__sub { padding-bottom: 2rpx; color: var(--mrc-text-sub); font-size: 20rpx; }

/* 锅仔形象馆入口 */
.profile-memory { display: flex; min-height: 138rpx; align-items: center; gap: 18rpx; padding: 22rpx 26rpx; box-sizing: border-box; margin-bottom: 16rpx; border: 2rpx solid var(--mrc-border); border-radius: 30rpx; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.profile-memory:active { transform: scale(.98); }
.profile-memory__guozai { width: 88rpx; height: 88rpx; flex-shrink: 0; }
.profile-memory__main { flex: 1; min-width: 0; }
.profile-memory__eyebrow, .profile-memory__title, .profile-memory__sub { display: block; }
.profile-memory__eyebrow { color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.profile-memory__title { margin-top: 4rpx; color: var(--mrc-text-deep); font-size: 32rpx; font-weight: var(--mrc-fw-heavy); }
.profile-memory__sub { margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.profile-memory__arrow { color: var(--mrc-text-light); font-size: 40rpx; }

.profile-gallery {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface-sun));
  border: 2rpx solid var(--mrc-border);
  border-radius: 32rpx;
  min-height: 138rpx;
  padding: 22rpx 26rpx;
  margin-bottom: 24rpx;
  box-sizing: border-box;
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
  transition: transform 0.15s ease;
}
.profile-gallery:active {
  transform: scale(0.98);
}
.profile-gallery__guozai {
  width: 88rpx;
  height: 88rpx;
  animation: guozai-breathe 3s ease-in-out infinite;
}
.profile-gallery__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.profile-gallery__title {
  font-size: 31rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
}
.profile-gallery__sub {
  font-size: 22rpx;
  color: var(--mrc-text-sub);
}
.profile-gallery__badge {
  font-size: 22rpx;
  color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
  padding: 8rpx 16rpx;
  border-radius: 24rpx;
  font-weight: 600;
}
.profile-gallery__arrow {
  font-size: 40rpx;
  color: var(--mrc-text-light);
}

/* 功能按钮 */
.profile-actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.profile-action {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  min-height: 168rpx;
  background: linear-gradient(145deg, var(--mrc-surface) 0%, var(--mrc-surface-peach) 100%);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 24rpx 16rpx;
  box-sizing: border-box;
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
  transition: transform 0.15s ease;
}
.profile-action__copy { min-width: 0; text-align: center; }
.profile-action__text {
  display: block;
  font-size: 28rpx;
  color: var(--mrc-text-deep);
  font-weight: 700;
}
.profile-action__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 20rpx; line-height: 1.35; }
.profile-action__guozai {
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
}
.profile-action:active {
  transform: scale(0.96);
}

/* 历史记录 */
.profile-history {
  position: relative;
  overflow: hidden;
  background: linear-gradient(150deg, var(--mrc-surface), var(--mrc-surface-peach));
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  padding: 26rpx;
  margin-bottom: 24rpx;
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
}
.profile-history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  margin-bottom: 18rpx;
}

.profile-history__head > view:first-child {
  display: flex;
  flex-direction: column;
  gap: 5rpx;
}

.profile-history__eyebrow {
  color: var(--mrc-accent);
  font-size: 18rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
}
.profile-history__month {
  font-size: 34rpx;
  font-weight: var(--mrc-fw-heavy);
  color: var(--mrc-text-deep);
}

.profile-history__all {
  display: flex;
  min-height: 72rpx;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 4rpx;
  color: var(--mrc-text-sub);
  font-size: 19rpx;
}

.profile-history__all text:last-child {
  color: var(--mrc-accent);
  font-size: 21rpx;
  font-weight: 800;
}
.profile-history__item {
  position: relative;
  display: grid;
  grid-template-columns: 116rpx minmax(0, 1fr) 36rpx;
  align-items: center;
  gap: 18rpx;
  min-height: 132rpx;
  margin-top: 14rpx;
  padding: 12rpx;
  box-sizing: border-box;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 26rpx;
  background: var(--mrc-surface);
  transition: transform 180ms ease-out, opacity 180ms ease-out;
}

.profile-history__item:active {
  transform: scale(.985);
  opacity: .82;
}

.profile-history__item--featured {
  display: block;
  overflow: hidden;
  padding: 0;
  border-radius: 30rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.profile-history__empty { display: flex; align-items: center; gap: 20rpx; min-height: 132rpx; padding: 8rpx 4rpx; }
.profile-history__empty image { width: 92rpx; height: 92rpx; flex-shrink: 0; }
.profile-history__empty-title, .profile-history__empty-sub { display: block; }
.profile-history__empty-title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.profile-history__empty-sub { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.45; }

.profile-history__photo {
  position: relative;
  width: 116rpx;
  height: 108rpx;
  overflow: hidden;
  border-radius: 20rpx;
  background: var(--mrc-surface-2);
}

.profile-history__photo image {
  width: 100%;
  height: 100%;
}

.profile-history__item--featured .profile-history__photo {
  width: 100%;
  height: 260rpx;
  border-radius: 0;
}
.profile-history__date {
  position: absolute;
  top: 10rpx;
  left: 10rpx;
  min-height: 42rpx;
  padding: 0 13rpx;
  display: flex;
  align-items: center;
  border-radius: 21rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-surface);
  box-shadow: var(--mrc-shadow-sm);
  font-size: 18rpx;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.profile-history__main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7rpx;
}

.profile-history__item--featured .profile-history__main {
  padding: 22rpx 66rpx 24rpx 22rpx;
}

.profile-history__title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10rpx;
}
.profile-history__dish {
  overflow: hidden;
  font-size: 29rpx;
  color: var(--mrc-text-deep);
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-history__item--featured .profile-history__dish {
  font-size: 34rpx;
}
.profile-history__mood {
  width: 38rpx;
  height: 38rpx;
  flex-shrink: 0;
}

.profile-history__meta {
  color: var(--mrc-accent);
  font-size: 20rpx;
  font-weight: 700;
}

.profile-history__note {
  overflow: hidden;
  color: var(--mrc-text-sub);
  font-size: 21rpx;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.profile-history__arrow {
  font-size: 36rpx;
  color: var(--mrc-text-light);
  flex-shrink: 0;
}

.profile-history__item--featured .profile-history__arrow {
  position: absolute;
  right: 24rpx;
  bottom: 30rpx;
}

.profile-history__companion {
  display: flex;
  min-height: 88rpx;
  align-items: center;
  gap: 10rpx;
  padding: 12rpx 6rpx 0;
  color: var(--mrc-text-sub);
  font-size: 20rpx;
  line-height: 1.45;
}

.profile-history__companion image {
  width: 66rpx;
  height: 66rpx;
  flex-shrink: 0;
}

@media (max-width: 350px) {
  .profile-history__item {
    grid-template-columns: 100rpx minmax(0, 1fr) 28rpx;
    gap: 12rpx;
  }
  .profile-history__photo {
    width: 100rpx;
  }
}

@media (prefers-reduced-motion: reduce) {
  .profile-history__item {
    transition: none;
  }
}

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
</style>
