<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'
import { ref, computed } from 'vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import { ensureLogin } from '../../utils/login'
import { fetchGrowthStatus, type GrowthStatus } from '@/api/growth'
import { toast, toastError, toastSuccess } from '../../utils/toast'
import SuccessModal from '../../components/guozai/SuccessModal.vue'
import {
  fetchCheckinStatus,
  doCheckin,
  type CheckinStatus,
} from '@/api/checkin'
import { fetchAchievements, checkAchievements, type AchievementItem, type AchievementView } from '../../api/achievements'

definePage({
  name: 'gallery',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '锅仔形象馆',
  },
})

import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'

// 直接本页写生命周期，比 useShare 组合式更可靠
onShareAppMessage(() => {
  const pages = getCurrentPages()
  const route = (pages[pages.length - 1] as any)?.route || ''
  return { title: '锅仔 · 按心情帮你决定今天吃什么', path: `/${route}` }
})
onShareTimeline(() => ({ title: '锅仔 · 按心情帮你决定今天吃什么' }))

const router = useRouter()

// ---------- 数据状态 ----------
const loading = ref(true)
const error = ref('')
const checkin = ref<CheckinStatus>({ checkedIn: false, streak: 0, totalDays: 0 })
const growth = ref<GrowthStatus | null>(null)
const checking = ref(false)
const showCheckinModal = ref(false)
/** 锅仔成就墙：记录/探索/连续天数等，记录落库后自动检测解锁 */
const achievements = ref<AchievementView | null>(null)
/** 徽章图加载失败的成就 code → 回退显示 emoji（COS 偶发抽风不至于开天窗） */
const badgeErrors = ref<Record<string, boolean>>({})
const achievementPct = computed(() => {
  const a = achievements.value
  if (!a || !a.total) return 0
  return Math.min(100, Math.round((a.unlockedCount / a.total) * 100))
})

/** 形象馆两馆合一：成就墙 / 表情包，Tab 切换（成就默认首位） */
const GALLERY_TABS = [
  { value: 'achievements', label: '成就' },
  { value: 'stickers', label: '表情包' },
] as const
type GalleryTab = (typeof GALLERY_TABS)[number]['value']
const activeTab = ref<GalleryTab>('achievements')
/** 成就未读数：进馆时从 storage 消费到页内承接（「我的」页红点由此归零），看过成就墙即清零 */
const achievementUnread = ref(0)
function openTab(tab: GalleryTab) {
  activeTab.value = tab
  if (tab === 'achievements')
    achievementUnread.value = 0
}

/** 点开一枚成就：解锁的看获取方式，未解锁的当作目标提示 */
function showAchievement(item: AchievementItem) {
  uni.showModal({
    title: `${item.emoji} ${item.name}`,
    content: `${item.unlocked ? '已解锁 · ' : '还没解锁 · '}${item.desc}`,
    showCancel: false,
    confirmText: item.unlocked ? '真棒' : '加油',
  })
}

// ---------- 表情包（内容资产区：锅仔透明 PNG 资源库） ----------
const STICKER_GROUPS = [
  {
    title: '心情表情',
    items: [
      { name: '开心', src: STATIC_BASE_URL + '/static/guozai/mood_01_happy.png' },
      { name: '平静', src: STATIC_BASE_URL + '/static/guozai/mood_02_calm.png' },
      { name: '疲惫', src: STATIC_BASE_URL + '/static/guozai/mood_03_tired.png' },
      { name: '焦虑', src: STATIC_BASE_URL + '/static/guozai/mood_04_anxious.png' },
      { name: '难过', src: STATIC_BASE_URL + '/static/guozai/mood_05_sad.png' },
      { name: '嘴馋', src: STATIC_BASE_URL + '/static/guozai/mood_06_hungry.png' },
      { name: '低落', src: STATIC_BASE_URL + '/static/guozai/mood_07_low.png' },
      { name: '想家', src: STATIC_BASE_URL + '/static/guozai/mood_08_homesick.png' },
    ],
  },
  {
    title: '动作表情',
    items: [
      { name: '端碗', src: STATIC_BASE_URL + '/static/guozai/action_01_bowl.png' },
      { name: '端汤', src: STATIC_BASE_URL + '/static/guozai/action_02_soup.png' },
      { name: '拍照', src: STATIC_BASE_URL + '/static/guozai/action_03_camera.png' },
      { name: '日历', src: STATIC_BASE_URL + '/static/guozai/action_04_calendar.png' },
      { name: '画册', src: STATIC_BASE_URL + '/static/guozai/action_05_album.png' },
      { name: '眼镜', src: STATIC_BASE_URL + '/static/guozai/action_06_glasses.png' },
      { name: '空碗', src: STATIC_BASE_URL + '/static/guozai/action_07_empty.png' },
      { name: '探头', src: STATIC_BASE_URL + '/static/guozai/action_08_peek.png' },
      { name: '庆祝', src: STATIC_BASE_URL + '/static/guozai/action_09_celebrate.png' },
      { name: '思考', src: STATIC_BASE_URL + '/static/guozai/action_10_thinking.png' },
    ],
  },
  {
    title: '状态表情',
    items: [
      { name: '空状态', src: STATIC_BASE_URL + '/static/guozai/state_01_empty.png' },
      { name: '加载中', src: STATIC_BASE_URL + '/static/guozai/state_02_loading.png' },
      { name: '网络错误', src: STATIC_BASE_URL + '/static/guozai/state_03_error.png' },
    ],
  },
]

const progressPct = computed(() => Math.min(100, Math.round((checkin.value.streak / 30) * 100)))

// ---------- 数据加载 ----------
async function loadData() {
  loading.value = true
  error.value = ''
  try {
    await ensureLogin()
    const [ck, achv, gr] = await Promise.all([
      fetchCheckinStatus(),
      fetchAchievements().catch(() => null),
      fetchGrowthStatus().catch(() => null),
    ])
    checkin.value = ck
    achievements.value = achv
    growth.value = gr
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onShow(() => {
  // 进馆即消费成就未读：「我的」页红点归零，未读数由本页 Tab 承接，看过成就墙即清零
  achievementUnread.value = Number(uni.getStorageSync('mrc_achv_unread') || 0)
  uni.removeStorageSync('mrc_achv_unread')
  loadData()
  // 「我的」页入口携带 focus=achievements 时直接定位成就 Tab
  const pages = getCurrentPages()
  const query = (pages[pages.length - 1] as any)?.options || {}
  if (query.focus === 'achievements')
    activeTab.value = 'achievements'
  // 兜底补检测（分享/生成周菜单等场景的成就在这里集中解锁），有新解锁就锅仔播报
  checkAchievements()
    .then((res) => {
      const codes = res?.newlyUnlocked || []
      if (!codes.length || !achievements.value)
        return
      achievementUnread.value += codes.length
      const names = codes
        .map(code => achievements.value?.achievements.find(item => item.code === code))
        .filter(Boolean)
        .map(item => `${item!.emoji} ${item!.name}`)
      if (!names.length)
        return
      uni.showModal({
        title: '锅仔播报',
        content: `解锁新成就：${names.join('、')}！去成就墙看看吧`,
        showCancel: false,
        confirmText: '好耶',
      })
    })
    .catch(() => {})
})

// ---------- 签到 ----------
/** 今日随机赠礼：对话券 → 去锅仔对话；三餐券 → 去今日三餐。获赠即引导，一步到达使用页 */
const giftType = computed(() => checkin.value.giftType || 'CONVERSATION')
const giftSubTitle = computed(() => giftType.value === 'MEAL_PLAN'
  ? `今日赠礼：三餐搭配券 ×2，已连续签到 ${checkin.value.streak} 天`
  : `今日赠礼：锅仔对话 ×3，已连续签到 ${checkin.value.streak} 天`)
const giftConfirmText = computed(() => giftType.value === 'MEAL_PLAN' ? '看今日三餐' : '去和锅仔聊聊')
function onCheckinConfirm() {
  showCheckinModal.value = false
  router.push({ name: giftType.value === 'MEAL_PLAN' ? 'daily-meal-plan' : 'meal-agent' })
}

async function onCheckin() {
  if (checkin.value.checkedIn || checking.value) return
  checking.value = true
  try {
    await ensureLogin()
    checkin.value = await doCheckin()
    showCheckinModal.value = true
  } catch (e: any) {
    toastError(e, '签到失败')
  } finally {
    checking.value = false
  }
}

// ---------- 表情包下载（小程序保存到相册） ----------
function onDownloadSticker(sticker: { name: string; src: string }) {
  // #ifndef MP-WEIXIN
  toast('请在小程序中体验下载表情包')
  // #endif
  // #ifdef MP-WEIXIN
  // saveImageToPhotosAlbum 只接受本地临时路径，不能直接传网络 URL：
  // 先 downloadFile 落盘为临时文件，再保存。
  uni.downloadFile({
    url: sticker.src,
    success: (res) => {
      if (res.statusCode === 200 && res.tempFilePath) {
        uni.saveImageToPhotosAlbum({
          filePath: res.tempFilePath,
          success: () => toastSuccess(`已保存「${sticker.name}」到相册`),
          fail: () => toast('保存失败，请检查相册权限'),
        })
      } else {
        toast('下载失败，请稍后重试')
      }
    },
    fail: () => toast('下载失败，请检查网络'),
  })
  // #endif
}

</script>

<template>
  <view class="gallery-page">
    <!-- 顶部导航（通用组件：状态栏 + 胶囊避让） -->
    <wd-navbar title="锅仔形象馆" left-arrow safe-area-inset-top @click-left="navBack"  custom-style="background-color: transparent !important;" /> 
    <!-- Loading / Error -->
    <LoadingState v-if="loading" text="锅仔正在布置形象馆..." />
    <ErrorState v-else-if="error" @retry="loadData" />

    <template v-else>
      <!-- 签到福利卡片 -->
      <view class="checkin-card">
        <view class="checkin-card__left">
          <view class="checkin-card__head">
            <text class="checkin-card__label">签到福利</text>
          </view>
          <view class="checkin-card__progress">
            <text class="checkin-card__streak">
              连续签到 <text class="checkin-card__num">{{ checkin.streak }}</text> 天
            </text>
            <text class="checkin-card__hint">每天签到，抽对话券或三餐券</text>
          </view>
          <view class="checkin-bar">
            <view class="checkin-bar__inner" :style="{ width: progressPct + '%' }" />
          </view>
        </view>
        <view class="checkin-card__right">
          <image
            class="checkin-card__guozai"
            :src="checkin.checkedIn ? STATIC_BASE_URL + '/static/guozai/mood_01_happy.png' : STATIC_BASE_URL + '/static/guozai/action_08_peek.png'"
            mode="aspectFit"
          />
          <view
            class="checkin-btn"
            :class="{ 'checkin-btn--done': checkin.checkedIn }"
            @click="onCheckin"
          >
            {{ checkin.checkedIn ? '今日已签到' : '签到' }}
          </view>
        </view>
      </view>

      <!-- 锅仔养成：记录/做菜/消耗临期食材 = 喂锅仔 → 升级解锁新形态（签到/成就/形象在这里汇成一条线） -->
      <view v-if="growth" class="growth-card">
        <view class="growth-card__head">
          <image class="growth-card__gz" :src="STATIC_BASE_URL + growth.skins[Math.min(growth.level - 1, growth.skins.length - 1)].image" mode="aspectFit" aria-hidden="true" />
          <view class="growth-card__copy">
            <text class="growth-card__label">锅仔养成 · LV.{{ growth.level }} {{ growth.levelName }}</text>
            <text class="growth-card__hint">{{ growth.hasNextLevel ? `再喂 ${growth.pointsToNext} 点升级` : '已是满级锅仙，锅仔谢谢你' }} · 今天投喂了 {{ growth.todayPoints }} 点</text>
          </view>
          <text class="growth-card__points">{{ growth.points }}</text>
        </view>
        <view class="growth-card__bar">
          <view class="growth-card__bar-fill" :style="{ width: `${Math.min(100, growth.progressPercent)}%` }" />
        </view>
        <view class="growth-card__skins">
          <view v-for="skin in growth.skins" :key="skin.level" class="growth-skin" :class="{ 'is-locked': !skin.unlocked }" :aria-label="skin.unlocked ? `已解锁${skin.name}` : `${skin.name}，${skin.unlockAtPoints} 点解锁`">
            <image class="growth-skin__img" :src="STATIC_BASE_URL + skin.image" mode="aspectFit" lazy-load />
            <text class="growth-skin__lv">LV.{{ skin.level }}</text>
          </view>
        </view>
        <text class="growth-card__foot">记录一餐 / 做计划菜 / 消灭临期食材都会喂锅仔——别让菜坏掉，锅仔会伤心</text>
      </view>

      <!-- ============ Tab：成就 / 表情包 ============ -->
      <view class="gallery-tabs">
        <view
          v-for="tab in GALLERY_TABS"
          :key="tab.value"
          class="gallery-tab"
          :class="{ 'gallery-tab--active': activeTab === tab.value }"
          role="tab"
          :aria-selected="activeTab === tab.value"
          @click="openTab(tab.value)"
        >
          {{ tab.label }}<text v-if="tab.value === 'achievements' && achievementUnread > 0" class="gallery-tab__dot">{{ achievementUnread }}</text>
        </view>
      </view>

      <!-- 成就墙：记录/探索/连续天数等，越用越亮 -->
      <view v-if="activeTab === 'achievements'">
        <view v-if="achievements" class="achv-section">
          <view class="section-title">
            <text>锅仔成就墙</text>
            <text class="section-title__sub">已点亮 {{ achievements.unlockedCount }} / {{ achievements.total }}</text>
          </view>
          <view class="achv-progress">
            <view class="achv-progress__inner" :style="{ width: achievementPct + '%' }" />
          </view>
          <view class="achv-grid">
            <view
              v-for="item in achievements.achievements"
              :key="item.code"
              class="achv-item"
              :class="{ 'achv-item--locked': !item.unlocked }"
              :aria-label="`${item.name}，${item.unlocked ? '已解锁' : '未解锁'}，${item.desc}`"
              role="button"
              @click="showAchievement(item)"
            >
              <!-- 解锁态优先显示 COS 徽章图（本地 PNG 已下线换 COS），挂了回退 emoji；锁定态保持锁图标 -->
              <image
                v-if="item.unlocked && item.badgeImage && !badgeErrors[item.code]"
                class="achv-item__img"
                :src="STATIC_BASE_URL + item.badgeImage"
                mode="aspectFit"
                lazy-load
                @error="badgeErrors[item.code] = true"
              />
              <text v-else class="achv-item__emoji">{{ item.unlocked ? item.emoji : '' }}</text>
              <text class="achv-item__name">{{ item.name }}</text>
            </view>
          </view>
          <text class="achv-hint">好好吃饭、多记录，成就徽章一个个点亮</text>
        </view>
      </view>

      <!-- 表情包 -->
      <view v-else>
        <view class="asset-intro">
          <image class="asset-intro__guozai" :src="STATIC_BASE_URL + '/static/guozai/mood_06_hungry.png'" mode="aspectFit" />
          <view class="asset-intro__text">
            <text class="asset-intro__title">锅仔专属表情包</text>
            <text class="asset-intro__sub">点击即可保存到相册，让锅仔住进你的聊天里</text>
          </view>
        </view>
        <view v-for="group in STICKER_GROUPS" :key="group.title" class="asset-group">
          <text class="asset-group__title">{{ group.title }}</text>
          <view class="asset-grid">
            <view
              v-for="s in group.items"
              :key="s.name"
              class="asset-item"
              @click="onDownloadSticker(s)"
            >
              <image class="asset-item__img" :src="s.src" mode="aspectFit" lazy-load />
              <text class="asset-item__name">{{ s.name }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="gallery-footer">
        <image class="gallery-footer__guozai" :src="STATIC_BASE_URL + '/static/guozai/mood_02_calm.png'" mode="aspectFit" />
        <text class="gallery-footer__text">「用一道菜，治愈今天的你。」</text>
      </view>
    </template>

    <!-- 签到成功弹窗：按今日随机赠礼类型定制，确认即直达使用页 -->
    <SuccessModal
      :visible="showCheckinModal"
      title="签到成功！"
      :subtitle="giftSubTitle"
      :confirm-text="giftConfirmText"
      @confirm="onCheckinConfirm"
    />
  </view>
</template>

<style lang="scss" scoped>
.gallery-page {
  min-height: 100vh;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(60rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 签到卡片 */
.checkin-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface-sun));
  border: 2rpx solid var(--mrc-border);
  border-radius: 32rpx;
  padding: 32rpx;
  margin: 12rpx 0 24rpx;
  box-shadow: var(--mrc-shadow);
}
.checkin-card__left {
  flex: 1;
  padding-right: 20rpx;
}
.checkin-card__head {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.checkin-card__label {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.checkin-card__streak {
  font-size: 28rpx;
  color: var(--mrc-text);
}
.checkin-card__num {
  font-size: 40rpx;
  font-weight: var(--mrc-fw-heavy);
  color: var(--mrc-accent);
  margin: 0 4rpx;
}
.checkin-card__hint {
  display: block;
  font-size: 24rpx;
  color: var(--mrc-text-sub);
  margin-top: 8rpx;
}
.checkin-bar {
  height: 16rpx;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8rpx;
  margin-top: 20rpx;
  overflow: hidden;
}
.checkin-bar__inner {
  height: 100%;
  background: linear-gradient(90deg, var(--mrc-primary), var(--mrc-accent));
  border-radius: 8rpx;
  transition: width 0.5s ease;
}
.checkin-card__right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.checkin-card__guozai {
  width: 120rpx;
  height: 120rpx;
}
.checkin-btn {
  padding: 12rpx 36rpx;
  border-radius: 40rpx;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  box-shadow: 0 6rpx 16rpx rgba(255, 139, 106, 0.4);
}
.checkin-btn--done {
  background: var(--mrc-green);
  box-shadow: none;
}
.checkin-btn:active {
  transform: scale(0.96);
}

/* Tab */
/* ===== 锅仔养成卡：等级/进度/皮肤墙，与签到卡同族语言 ===== */
.growth-card {
  margin-top: 18rpx;
  padding: 26rpx 28rpx 22rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 30rpx;
  background: linear-gradient(160deg, #fffdf6, var(--mrc-surface-sun));
  box-shadow: var(--mrc-shadow-soft);
}
.growth-card__head { display: flex; align-items: center; gap: 18rpx; }
.growth-card__gz { width: 96rpx; height: 96rpx; flex: 0 0 96rpx; }
.growth-card__copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 6rpx; }
.growth-card__label { color: var(--mrc-text-strong); font-size: 28rpx; font-weight: 800; }
.growth-card__hint { color: var(--mrc-text-sub); font-size: 21rpx; }
.growth-card__points { flex-shrink: 0; color: var(--mrc-accent); font-size: 36rpx; font-weight: 900; }
.growth-card__bar { height: 14rpx; margin-top: 16rpx; border-radius: 999rpx; background: var(--mrc-surface-2); overflow: hidden; }
.growth-card__bar-fill { height: 100%; border-radius: 999rpx; background: var(--mrc-primary-grad); transition: width .4s ease; }
.growth-card__skins { display: flex; justify-content: space-between; gap: 10rpx; margin-top: 20rpx; }
.growth-skin { display: flex; flex: 1; min-width: 0; flex-direction: column; align-items: center; gap: 4rpx; padding: 10rpx 4rpx; border-radius: 16rpx; background: var(--mrc-surface); }
.growth-skin__img { width: 64rpx; height: 64rpx; }
.growth-skin__lv { color: var(--mrc-text-sub); font-size: 17rpx; font-weight: 700; }
.growth-skin.is-locked .growth-skin__img { filter: grayscale(1); opacity: .35; }
.growth-skin.is-locked .growth-skin__lv { color: var(--mrc-text-light); }
.growth-card__foot { display: block; margin-top: 14rpx; color: var(--mrc-text-light); font-size: 19rpx; line-height: 1.5; }

.gallery-tabs {
  display: flex;
  background: var(--mrc-surface-2);
  border-radius: 50rpx;
  padding: 8rpx;
  margin-bottom: 32rpx;
}
.gallery-tab {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  border-radius: 40rpx;
  font-size: 30rpx;
  color: var(--mrc-text-sub);
  font-weight: 500;
  transition: all 0.2s ease;
}
.gallery-tab--active {
  background: var(--mrc-surface);
  color: var(--mrc-accent);
  font-weight: 700;
  box-shadow: 0 4rpx 12rpx rgba(90, 62, 43, 0.08);
}
.gallery-tab__dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30rpx;
  height: 30rpx;
  margin-left: 8rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: var(--mrc-accent);
  color: #fff;
  font-size: 20rpx;
  font-weight: 800;
}

/* 成就墙 */
.achv-section {
  margin: 0 0 24rpx;
  padding: 28rpx 24rpx 24rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.achv-progress {
  height: 14rpx;
  background: var(--mrc-surface-2);
  border-radius: 999rpx;
  margin: 4rpx 4rpx 24rpx;
  overflow: hidden;
}
.achv-progress__inner {
  height: 100%;
  background: linear-gradient(90deg, var(--mrc-primary), var(--mrc-accent));
  border-radius: 999rpx;
  transition: width 0.5s ease;
}
.achv-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
}
.achv-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 18rpx 6rpx;
  border-radius: 20rpx;
  background: var(--mrc-accent-soft);
  border: 2rpx solid rgba(232, 131, 107, 0.25);
  transition: transform 0.15s ease;
}
.achv-item:active {
  transform: scale(0.94);
}
.achv-item__emoji {
  font-size: 44rpx;
  line-height: 1.2;
}
.achv-item__img {
  width: 88rpx;
  height: 88rpx;
}
.achv-item__name {
  font-size: 20rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.achv-item--locked {
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
}
.achv-item--locked .achv-item__emoji {
  opacity: 0.55;
  filter: grayscale(1);
}
.achv-item--locked .achv-item__name {
  color: var(--mrc-text-light);
  font-weight: 500;
}
.achv-hint {
  display: block;
  margin-top: 20rpx;
  font-size: 22rpx;
  color: var(--mrc-text-light);
  text-align: center;
}

/* 内容资产 */
.asset-intro {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.asset-intro__guozai {
  width: 100rpx;
  height: 100rpx;
}
.asset-intro__text {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.asset-intro__title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.asset-intro__sub {
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}
.asset-group {
  margin-bottom: 32rpx;
}
.asset-group__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--mrc-text);
  display: block;
  margin-bottom: 16rpx;
  padding-left: 16rpx;
  border-left: 8rpx solid var(--mrc-primary);
}
.asset-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}
.asset-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx;
  padding: 20rpx 8rpx;
  transition: all 0.15s ease;
}
.asset-item:active {
  transform: scale(0.94);
  border-color: var(--mrc-primary);
}
.asset-item__img {
  width: 110rpx;
  height: 110rpx;
}
.asset-item__name {
  font-size: 22rpx;
  color: var(--mrc-text-sub);
}

/* 底部 */
.gallery-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 40rpx 0 20rpx;
}
.gallery-footer__guozai {
  width: 60rpx;
  height: 60rpx;
}
.gallery-footer__text {
  font-size: 24rpx;
  color: var(--mrc-text-light);
}

</style>
