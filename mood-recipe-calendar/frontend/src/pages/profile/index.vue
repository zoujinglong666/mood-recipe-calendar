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
  if (!tempPath) return
  try {
    const uploaded = await uploadFile(tempPath)
    await updateUserInfo({
      openid: userStore.openid,
      avatarUrl: uploaded.url,
    })
    await refreshUserInfo()
    uni.showToast({ title: '头像已更新', icon: 'success' })
  } catch {
    uni.showToast({ title: '头像更新失败', icon: 'none' })
  }
}

// ---- 微信昵称填写（小程序）----
function onNickConfirm() {
  const name = nickInput.value.trim()
  if (!name) return
  editingNick.value = false
  updateUserInfo({
    openid: userStore.openid,
    nickname: name,
  }).then(async () => {
    await refreshUserInfo()
    uni.showToast({ title: '昵称已更新', icon: 'success' })
  }).catch(() => {
    uni.showToast({ title: '昵称更新失败', icon: 'none' })
  })
}

function goReport() {
  router.push({ name: 'report' })
}
function goGallery() {
  router.push({ name: 'gallery' })
}
function goSettings() {
  router.push({ name: 'about' })
}
function showPrivacy() {
  uni.showModal({ title: '隐私政策', content: '我们仅在你主动记录时保存菜品、心情和图片，用于生成日历与画册；不会出售个人信息。AI 请求只使用本次生成所需的内容。你可随时联系客服申请导出或删除数据。', showCancel: false, confirmText: '我知道了' })
}
function goAbout() { router.push({ name: 'about' }) }
</script>

<template>
  <view class="profile-page">
    <!-- 我的页不展示整屏缺省图：直接渲染真实内容，数据就绪后响应式更新 -->
    <!-- 顶部用户信息（paddingTop 避开状态栏，右侧齿轮避开胶囊） -->
      <view class="profile-header" :style="{ paddingTop: nav.statusBarHeight + 24 + 'px' }">
        <view class="profile-header__left">
          <!-- 微信小程序：头像昵称填写能力 -->
          <!-- #ifdef MP-WEIXIN -->
          <button class="profile-avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
            <view class="profile-avatar">
              <image
                v-if="userStore.userInfo?.avatarUrl"
                class="profile-avatar__img"
                :src="userStore.userInfo.avatarUrl"
                mode="aspectFill"
              />
              <image v-else class="profile-avatar__img" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
            </view>
          </button>
          <!-- #endif -->
          <!-- H5 开发调试 -->
          <!-- #ifndef MP-WEIXIN -->
          <view class="profile-avatar">
            <image class="profile-avatar__img" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
          </view>
          <!-- #endif -->

          <!-- 昵称：小程序可填写，H5 只读 -->
          <!-- #ifdef MP-WEIXIN -->
          <input
            v-if="editingNick"
            v-model="nickInput"
            class="profile-nick-input"
            type="nickname"
            placeholder="点击填写昵称"
            confirm-type="done"
            @confirm="onNickConfirm"
            @blur="onNickConfirm"
          />
          <text v-else class="profile-name" @click="editingNick = true; nickInput = userStore.userInfo?.nickname || ''">
            {{ userStore.userInfo?.nickname || '点击设置昵称' }}
          </text>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <text class="profile-name">{{ userStore.userInfo?.nickname || '小圆' }}</text>
          <!-- #endif -->
        </view>
        <view class="profile-header__right" :style="{ marginRight: nav.capsuleRightGap + 8 + 'px' }" @click="goSettings">
          <Icon name="gear" :size="44" color="var(--mrc-text-deep)" />
        </view>
      </view>

      <!-- 统计卡 -->
      <view class="profile-stats">
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

      <!-- 锅仔形象馆入口（核心变现模块） -->
      <view class="profile-gallery" @click="goGallery">
        <image class="profile-gallery__guozai" src="/static/guozai/mood_06_hungry.png" mode="aspectFit" />
        <view class="profile-gallery__main">
          <text class="profile-gallery__title">锅仔形象馆</text>
          <text class="profile-gallery__sub">表情包 · 周边 · 签到福利</text>
        </view>
        <view class="profile-gallery__badge">签到兑周边</view>
        <text class="profile-gallery__arrow">›</text>
      </view>

      <!-- 功能按钮 -->
      <view class="profile-actions">
        <view class="profile-action" @click="goReport">
          <Icon name="camera" :size="44" color="var(--mrc-primary)" />
          <text class="profile-action__text">我的年度报告</text>
        </view>
        <view class="profile-action" @click="router.push({ name: 'calendar' })">
          <Icon name="list" :size="44" color="var(--mrc-primary)" />
          <text class="profile-action__text">历史记录</text>
        </view>
      </view>

      <!-- 历史记录列表 -->
      <view class="profile-history">
        <view class="profile-history__head">
          <text class="profile-history__month">最近记录</text>
          <text class="profile-history__arrow">›</text>
        </view>
        <view v-if="history.length === 0" class="profile-history__empty">
          <text>还没有记录，去做一道好吃的吧～</text>
        </view>
        <view
          v-for="(item, i) in history"
          :key="i"
          class="profile-history__item"
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
        <text class="profile-footer__link" @click="showPrivacy">隐私政策</text>
        <!-- 微信原生客服会话；需在小程序后台配置客服能力 -->
        <!-- #ifdef MP-WEIXIN -->
        <button class="profile-footer__link profile-footer__contact" open-type="contact">反馈建议</button>
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <text class="profile-footer__link" @click="uni.showToast({ title: '请在微信小程序中联系锅仔', icon: 'none' })">反馈建议</text>
        <!-- #endif -->
        <text class="profile-footer__link" @click="goAbout">关于我们</text>
        <image class="profile-footer__guozai" src="/static/guozai/mood_01_happy.png" mode="aspectFit" />
      </view>
  </view>
</template>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 顶部用户信息 */
.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 60rpx 8rpx 32rpx;
}
.profile-header__left {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.profile-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: var(--mrc-surface-peach);
  border: 4rpx solid var(--mrc-border-light);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.profile-avatar-btn {
  background: transparent;
  padding: 0;
  margin: 0;
  line-height: 1;
  border: none;
  border-radius: 50%;
}
.profile-avatar-btn::after {
  border: none;
}
.profile-avatar__img {
  width: 90rpx;
  height: 90rpx;
}
.profile-nick-input {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  min-width: 200rpx;
}
.profile-name {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.profile-header__right {
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 统计卡 */
.profile-stats {
  display: flex;
  align-items: center;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  padding: 36rpx 0;
  margin-bottom: 24rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.profile-stats__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.profile-stats__label {
  font-size: 28rpx;
  color: var(--mrc-text-sub);
}
.profile-stats__value {
  font-size: 64rpx;
  font-weight: 900;
  color: var(--mrc-text-deep);
  line-height: 1;
}
.profile-stats__unit {
  font-size: 32rpx;
  font-weight: 600;
  margin-left: 4rpx;
}
.profile-stats__divider {
  width: 2rpx;
  height: 80rpx;
  background: var(--mrc-border-light);
}

/* 锅仔形象馆入口 */
.profile-gallery {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface-sun));
  border: 2rpx solid var(--mrc-border);
  border-radius: 32rpx;
  padding: 28rpx 32rpx;
  margin-bottom: 32rpx;
  box-shadow: var(--mrc-shadow-soft);
  transition: transform 0.15s ease;
}
.profile-gallery:active {
  transform: scale(0.98);
}
.profile-gallery__guozai {
  width: 100rpx;
  height: 100rpx;
  animation: guozai-breathe 3s ease-in-out infinite;
}
.profile-gallery__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.profile-gallery__title {
  font-size: 34rpx;
  font-weight: 800;
  color: var(--mrc-text-deep);
}
.profile-gallery__sub {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}
.profile-gallery__badge {
  font-size: 22rpx;
  color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
  padding: 6rpx 18rpx;
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
  gap: 24rpx;
  margin-bottom: 32rpx;
}
.profile-action {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 32rpx 0;
  box-shadow: var(--mrc-shadow-soft);
}
.profile-action__text {
  font-size: 32rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.profile-action:active {
  transform: scale(0.97);
}

/* 历史记录 */
.profile-history {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  padding: 28rpx;
  margin-bottom: 40rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.profile-history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20rpx;
  border-bottom: 2rpx solid var(--mrc-border-light);
  margin-bottom: 8rpx;
}
.profile-history__month {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.profile-history__arrow {
  font-size: 36rpx;
  color: var(--mrc-text-light);
}
.profile-history__item {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 2rpx solid var(--mrc-border-light);
}
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
  gap: 48rpx;
  padding: 20rpx 0;
  position: relative;
}
.profile-footer__link {
  font-size: 26rpx;
  color: var(--mrc-text-sub);
}
.profile-footer__contact { margin: 0; padding: 0; line-height: inherit; background: transparent; border: 0; }
.profile-footer__contact::after { border: 0; }
.profile-footer__guozai {
  position: absolute;
  right: 0;
  bottom: -10rpx;
  width: 100rpx;
  height: 100rpx;
}
</style>
