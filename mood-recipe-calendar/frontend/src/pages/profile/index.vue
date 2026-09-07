<script setup lang="ts">
import { ref } from 'vue'
import Icon from '../../components/common/Icon.vue'
import { ensureLogin, refreshUserInfo } from '../../utils/login'
import { fetchStats, fetchRecords, type RecordItem } from '../../api/records'
import { updateUserInfo } from '../../api/auth'
import { uploadFile } from '../../api/request'
import { useUserStore } from '../../stores/user'
import { useNavBar } from '@/composables/useNavBar'

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
const stats = ref({ totalRecords: 0, totalDays: 0, currentStreak: 0, topDishes: [] as { name: string; count: number }[] })
const history = ref<RecordItem[]>([])
// 编辑昵称状态
const editingNick = ref(false)
const nickInput = ref('')
const avatarUpdating = ref(false)
const nickSaving = ref(false)

const MOOD_IMG_MAP: Record<string, string> = {
  开心: '/static/guozai/mood_01_happy.png',
  平静: '/static/guozai/mood_02_calm.png',
  疲惫: '/static/guozai/mood_03_tired.png',
  焦虑: '/static/guozai/mood_04_anxious.png',
  难过: '/static/guozai/mood_05_sad.png',
  嘴馋: '/static/guozai/mood_06_hungry.png',
  低落: '/static/guozai/mood_07_low.png',
  想家: '/static/guozai/mood_08_homesick.png',
}

async function loadData() {
  loading.value = true
  try {
    const openid = await ensureLogin()
    await refreshUserInfo()
    const [statsData, records] = await Promise.all([
      fetchStats(openid),
      fetchRecords(openid),
    ])
    stats.value = statsData
    history.value = records.slice(0, 10)
    nickInput.value = userStore.userInfo?.nickname || ''
  } catch (e: any) {
    // 我的页登录/加载失败不展示缺省图，仅 toast 轻提示
    uni.showToast({ title: e.message || '加载失败，请稍后重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

onShow(() => {
  loadData()
})

// ---- 微信头像授权（小程序）----
async function onChooseAvatar(e: any) {
  const tempPath = e.detail?.avatarUrl
  if (!tempPath || avatarUpdating.value) return
  avatarUpdating.value = true
  try {
    await ensureLogin()
    const uploaded = await uploadFile(tempPath)
    await updateUserInfo({
      openid: userStore.openid,
      avatarUrl: uploaded.url,
    })
    await refreshUserInfo()
    uni.showToast({ title: '头像已更新', icon: 'success' })
  } catch {
    uni.showToast({ title: '头像更新失败', icon: 'none' })
  } finally {
    avatarUpdating.value = false
  }
}

// ---- 微信昵称填写（小程序）----
async function onNickConfirm() {
  const name = nickInput.value.trim()
  if (!name || nickSaving.value) return
  nickSaving.value = true
  try {
    await ensureLogin()
    await updateUserInfo({ openid: userStore.openid, nickname: name })
    await refreshUserInfo()
    editingNick.value = false
    uni.showToast({ title: '昵称已更新', icon: 'success' })
  } catch {
    uni.showToast({ title: '昵称更新失败', icon: 'none' })
  } finally {
    nickSaving.value = false
  }
}

function goReport() {
  router.push({ name: 'report' })
}
function goGallery() {
  router.push({ name: 'gallery' })
}
function goSettings() {
  router.push({ name: 'privacy' })
}
function showPrivacy() {
  router.push({ name: 'privacy' })
}
function goAbout() { router.push({ name: 'about' }) }
function goTimeline() { router.push({ name: 'timeline' }) }
function goPreferences() { router.push({ name: 'preferences' }) }
function onFeedbackHint() { uni.showToast({ title: '请在微信小程序中联系锅仔', icon: 'none' }) }
function goFeedback() { router.push({ name: 'feedback' }) }
</script>

<template>
  <view class="profile-page mrc-hero">
      <view
        class="profile-topbar"
        :style="{ paddingTop: nav.statusBarHeight + 'px', minHeight: nav.navBarHeight + 'px', paddingRight: nav.capsuleRightGap + 'px' }"
      >
        <text class="profile-topbar__title">我的</text>
        <view class="profile-topbar__settings" role="button" aria-label="打开设置" @click="goSettings">
          <Icon name="gear" :size="38" color="#EF5A3C" />
        </view>
      </view>

      <view class="profile-identity">
        <view class="profile-identity__glow profile-identity__glow--one" />
        <view class="profile-identity__glow profile-identity__glow--two" />
        <image class="profile-identity__companion" src="/static/guozai/action_08_peek.png" mode="aspectFit" />

        <view class="profile-identity__main">
          <!-- #ifdef MP-WEIXIN -->
          <button class="profile-avatar-btn" open-type="chooseAvatar" :disabled="avatarUpdating" aria-label="更换微信头像" @chooseavatar="onChooseAvatar">
            <view class="profile-avatar" :class="{ 'profile-avatar--logged': userStore.userInfo?.avatarUrl }">
              <image
                v-if="userStore.userInfo?.avatarUrl"
                class="profile-avatar__img"
                :src="userStore.userInfo.avatarUrl"
                mode="aspectFill"
              />
              <image v-else class="profile-avatar__img" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
              <view class="profile-avatar__edit">
                <Icon name="camera" :size="26" color="var(--mrc-accent)" />
              </view>
              <view v-if="avatarUpdating" class="profile-avatar__loading">上传中</view>
            </view>
          </button>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <view class="profile-avatar profile-avatar--logged">
            <image class="profile-avatar__img" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
          </view>
          <!-- #endif -->

          <view class="profile-userinfo">
            <text class="profile-userinfo__eyebrow">锅仔的小饭友</text>
            <!-- #ifdef MP-WEIXIN -->
            <input
              v-if="editingNick"
              v-model="nickInput"
              class="profile-nick-input"
              type="nickname"
              placeholder="请输入昵称"
              confirm-type="done"
              :disabled="nickSaving"
              @confirm="onNickConfirm"
              @blur="onNickConfirm"
            />
            <view v-else class="profile-name-wrap" @click="editingNick = true; nickInput = userStore.userInfo?.nickname || ''">
              <text class="profile-name">{{ userStore.userInfo?.nickname || '给自己取个昵称' }}</text>
              <text class="profile-name__arrow">›</text>
            </view>
            <text class="profile-login-hint">{{ userStore.userInfo?.nickname ? '点击昵称可修改' : '锅仔以后就这样称呼你' }}</text>
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <text class="profile-name">{{ userStore.userInfo?.nickname || '小圆' }}</text>
            <!-- #endif -->
          </view>
        </view>

        <view class="profile-identity__footer">
          <view class="profile-login-state">
            <view class="profile-login-state__dot" :class="{ 'profile-login-state__dot--online': userStore.isLoggedIn }" />
            <text>{{ userStore.isLoggedIn ? '微信身份已连接' : '正在连接微信身份…' }}</text>
          </view>
          <text class="profile-identity__promise">今天也要好好吃饭</text>
        </view>
      </view>

      <!-- 统计卡 -->
      <view class="profile-stats" aria-label="我的饮食记录统计">
        <view class="profile-stats__item">
          <text class="profile-stats__label">总记录</text>
          <text class="profile-stats__value">{{ stats.totalRecords }}<text class="profile-stats__unit">条</text></text>
        </view>
        <view class="profile-stats__divider" />
        <view class="profile-stats__item">
          <text class="profile-stats__label">记录天数</text>
          <text class="profile-stats__value">{{ stats.totalDays }}<text class="profile-stats__unit">天</text></text>
        </view>
        <view class="profile-stats__divider" />
        <view class="profile-stats__item">
          <text class="profile-stats__label">连续打卡</text>
          <text class="profile-stats__value">{{ stats.currentStreak }}<text class="profile-stats__unit">天</text></text>
        </view>
      </view>

      <view class="profile-section-head">
        <view><text class="profile-section-head__eyebrow">锅仔陪你</text><text class="profile-section-head__title">更懂你的每一餐</text></view>
        <text class="profile-section-head__sub">慢慢记录，慢慢熟悉</text>
      </view>

      <view class="profile-memory" role="button" aria-label="打开我的口味与忌口" @click="goPreferences">
        <image class="profile-memory__guozai" src="/static/guozai/action_10_thinking.png" mode="aspectFit" />
        <view class="profile-memory__main">
          <text class="profile-memory__eyebrow">锅仔会一直记得</text>
          <text class="profile-memory__title">我的口味与忌口</text>
          <text class="profile-memory__sub">喜欢什么 · 葱和香菜 · 辣度 · 过敏食材</text>
        </view>
        <text class="profile-memory__arrow">›</text>
      </view>

      <!-- 锅仔形象馆入口（核心变现模块） -->
      <view class="profile-gallery" role="button" aria-label="打开锅仔形象馆" @click="goGallery">
        <image class="profile-gallery__guozai" src="/static/guozai/mood_06_hungry.png" mode="aspectFit" />
        <view class="profile-gallery__main">
          <text class="profile-gallery__title">锅仔形象馆</text>
          <text class="profile-gallery__sub">表情包 · 主题素材 · 每日收藏</text>
        </view>
        <view class="profile-gallery__badge">每日签到</view>
        <text class="profile-gallery__arrow">›</text>
      </view>

      <!-- 功能按钮 -->
      <view class="profile-actions">
        <view class="profile-action" role="button" aria-label="打开我的年度报告" @click="goReport">
          <image class="profile-action__guozai" src="/static/guozai/action_09_celebrate.png" mode="aspectFit" />
          <view class="profile-action__copy"><text class="profile-action__text">年度报告</text><text class="profile-action__sub">看看这一年的味道</text></view>
        </view>
        <view class="profile-action" role="button" aria-label="打开菜谱时光机" @click="goTimeline">
          <image class="profile-action__guozai" src="/static/guozai/action_06_glasses.png" mode="aspectFit" />
          <view class="profile-action__copy"><text class="profile-action__text">菜谱时光机</text><text class="profile-action__sub">往回翻每一顿饭</text></view>
        </view>
      </view>

      <!-- 历史记录列表 -->
      <view class="profile-history">
        <view class="profile-history__head" role="button" aria-label="查看全部菜谱记录" @click="goTimeline">
          <text class="profile-history__month">最近记录</text>
          <text class="profile-history__arrow">›</text>
        </view>
        <view v-if="history.length === 0" class="profile-history__empty">
          <image src="/static/guozai/action_08_peek.png" mode="aspectFit" />
          <view><text class="profile-history__empty-title">第一顿饭，等你来记</text><text class="profile-history__empty-sub">记录后，锅仔会把它收进时光机</text></view>
        </view>
        <view
          v-for="(item, i) in history"
          :key="i"
          class="profile-history__item"
          @click="goTimeline"
        >
          <text class="profile-history__date">{{ item.recordDate?.slice(5) }}</text>
          <view class="profile-history__main">
            <text class="profile-history__dish">{{ item.dishName }}</text>
            <image class="profile-history__mood" :src="MOOD_IMG_MAP[item.moodTag] || '/static/guozai/mood_01_happy.png'" mode="aspectFit" />
          </view>
          <text class="profile-history__arrow">›</text>
        </view>
      </view>

      <!-- 底部链接 -->
      <view class="profile-footer">
        <text class="profile-footer__link" role="button" @click="showPrivacy">隐私政策</text>
        <view class="profile-footer__dot" />
        <text class="profile-footer__link" role="button" @click="goFeedback">反馈建议</text>
        <view class="profile-footer__dot" />
        <text class="profile-footer__link" role="button" @click="goAbout">关于我们</text>
        <image class="profile-footer__guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
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
.profile-topbar__title { color: var(--mrc-text-strong); font-size: 42rpx; font-weight: 900; }
.profile-topbar__settings { width: 88rpx; height: 88rpx; display: flex; align-items: center; justify-content: center; border: 2rpx solid var(--mrc-border); border-radius: 50%; background: var(--mrc-surface-peach); box-shadow: var(--mrc-shadow-sm); }
.profile-topbar__settings:active { transform: scale(.94); }

/* 身份卡 */
.profile-identity { position: relative; overflow: hidden; margin: 8rpx 0 24rpx; padding: 34rpx 30rpx 24rpx; border: 2rpx solid var(--mrc-border); border-radius: 40rpx; background: linear-gradient(145deg, var(--mrc-surface) 0%, var(--mrc-surface-peach) 58%, var(--mrc-surface-sun) 100%); box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss); }
.profile-identity__glow { position: absolute; border-radius: 50%; pointer-events: none; }
.profile-identity__glow--one { width: 250rpx; height: 250rpx; right: -90rpx; top: -120rpx; background: rgba(255, 197, 61, .18); }
.profile-identity__glow--two { width: 180rpx; height: 180rpx; left: -100rpx; bottom: -120rpx; background: rgba(255, 107, 91, .09); }
.profile-identity__companion { position: absolute; right: 6rpx; bottom: 38rpx; width: 126rpx; height: 126rpx; opacity: .86; pointer-events: none; }
.profile-identity__main { position: relative; z-index: 1; display: flex; align-items: center; gap: 26rpx; padding-right: 76rpx; }
.profile-userinfo { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 7rpx; }
.profile-userinfo__eyebrow { color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; letter-spacing: 2rpx; }
.profile-avatar { position: relative; width: 128rpx; height: 128rpx; display: flex; flex-shrink: 0; align-items: center; justify-content: center; overflow: visible; border: 6rpx solid rgba(255, 255, 255, .86); border-radius: 50%; background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface-sun)); box-shadow: 0 10rpx 24rpx rgba(148, 91, 56, .16); }
.profile-avatar--logged { background: var(--mrc-surface-peach); }
.profile-avatar-btn { width: 140rpx; height: 140rpx; flex-shrink: 0; margin: 0; padding: 0; border: 0; border-radius: 50%; background: transparent; line-height: 1; }
.profile-avatar-btn::after { border: 0; }
.profile-avatar-btn[disabled] { opacity: .7; }
.profile-avatar__img { width: 118rpx; height: 118rpx; border-radius: 50%; }
.profile-avatar__edit { position: absolute; right: -4rpx; bottom: -4rpx; width: 50rpx; height: 50rpx; display: flex; align-items: center; justify-content: center; border: 3rpx solid var(--mrc-surface); border-radius: 50%; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); }
.profile-avatar__loading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: rgba(61, 37, 25, .58); color: #fff; font-size: 21rpx; font-weight: 700; }
.profile-name-wrap { display: flex; min-height: 88rpx; align-items: center; gap: 4rpx; }
.profile-name { max-width: 360rpx; overflow: hidden; color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 850; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.profile-name__arrow { color: var(--mrc-text-light); font-size: 34rpx; line-height: 1; }
.profile-login-hint { color: var(--mrc-text-sub); font-size: 22rpx; font-weight: 500; line-height: 1.4; }
.profile-nick-input { width: 100%; height: 62rpx; padding: 0; border: 0; border-bottom: 2rpx solid var(--mrc-accent); background: transparent; color: var(--mrc-text-deep); font-size: 38rpx; font-weight: 800; }
.profile-identity__footer { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-top: 26rpx; padding-top: 20rpx; border-top: 2rpx solid rgba(226, 189, 152, .55); }
.profile-login-state { display: flex; align-items: center; gap: 10rpx; color: var(--mrc-text-sub); font-size: 21rpx; font-weight: 600; }
.profile-login-state__dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--mrc-text-light); }
.profile-login-state__dot--online { background: var(--mrc-mint); box-shadow: 0 0 0 6rpx rgba(40, 194, 160, .12); }
.profile-identity__promise { padding-right: 92rpx; color: var(--mrc-accent); font-size: 21rpx; font-weight: 700; }

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
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9rpx;
}
.profile-stats__label {
  font-size: 22rpx;
  color: var(--mrc-text-sub);
}
.profile-stats__value {
  font-size: 46rpx;
  font-weight: 900;
  color: var(--mrc-text-deep);
  line-height: 1;
}
.profile-stats__unit {
  font-size: 23rpx;
  font-weight: 600;
  margin-left: 4rpx;
}
.profile-stats__divider {
  width: 2rpx;
  height: 64rpx;
  background: var(--mrc-border-light);
}

.profile-section-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20rpx; margin: 0 4rpx 18rpx; }
.profile-section-head__eyebrow, .profile-section-head__title { display: block; }
.profile-section-head__eyebrow { margin-bottom: 6rpx; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.profile-section-head__title { color: var(--mrc-text-strong); font-size: 32rpx; font-weight: 850; }
.profile-section-head__sub { padding-bottom: 2rpx; color: var(--mrc-text-sub); font-size: 20rpx; }

/* 锅仔形象馆入口 */
.profile-memory { display: flex; min-height: 138rpx; align-items: center; gap: 18rpx; padding: 22rpx 26rpx; box-sizing: border-box; margin-bottom: 16rpx; border: 2rpx solid var(--mrc-border); border-radius: 30rpx; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.profile-memory:active { transform: scale(.98); }
.profile-memory__guozai { width: 88rpx; height: 88rpx; flex-shrink: 0; }
.profile-memory__main { flex: 1; min-width: 0; }
.profile-memory__eyebrow, .profile-memory__title, .profile-memory__sub { display: block; }
.profile-memory__eyebrow { color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.profile-memory__title { margin-top: 4rpx; color: var(--mrc-text-deep); font-size: 32rpx; font-weight: 850; }
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
  align-items: center;
  justify-content: flex-start;
  gap: 12rpx;
  min-height: 138rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 20rpx;
  box-sizing: border-box;
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
}
.profile-action__copy { min-width: 0; }
.profile-action__text {
  display: block;
  font-size: 27rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.profile-action__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.35; }
.profile-action__guozai {
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
}
.profile-action:active {
  transform: scale(0.97);
}

/* 历史记录 */
.profile-history {
  background: var(--mrc-surface);
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
  padding-bottom: 20rpx;
  border-bottom: 2rpx solid var(--mrc-border-light);
  margin-bottom: 16rpx;
}
.profile-history__month {
  font-size: 32rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
}
.profile-history__arrow {
  font-size: 36rpx;
  color: var(--mrc-text-light);
}
.profile-history__item {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  padding: 8rpx 0;
  border-bottom: 2rpx solid var(--mrc-border-light);
}
.profile-history__empty { display: flex; align-items: center; gap: 20rpx; min-height: 132rpx; padding: 8rpx 4rpx; }
.profile-history__empty image { width: 92rpx; height: 92rpx; flex-shrink: 0; }
.profile-history__empty-title, .profile-history__empty-sub { display: block; }
.profile-history__empty-title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.profile-history__empty-sub { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.45; }
.profile-history__item:last-child {
  border-bottom: none;
}
.profile-history__date {
  font-size: 26rpx;
  color: var(--mrc-text-sub);
  width: 100rpx;
  flex-shrink: 0;
}
.profile-history__main {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.profile-history__dish {
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  font-weight: 500;
}
.profile-history__mood {
  width: 40rpx;
  height: 40rpx;
}
.profile-history__arrow {
  font-size: 32rpx;
  color: var(--mrc-text-light);
  flex-shrink: 0;
}

/* 底部 */
.profile-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  min-height: 112rpx;
  padding: 8rpx 94rpx 8rpx 0;
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
