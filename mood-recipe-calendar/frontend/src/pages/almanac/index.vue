<script setup lang="ts">
/**
 * 锅仔老黄历：今日宜吃 / 摇签定今晚的菜。
 *
 * 诚实抽签原则（与 GuozaiWheel 同源）：签文里的菜永远是真实接口
 * 返回的那道菜——摇签动画与请求并行跑，摇完把真菜连名带图写进签文，
 * 绝不假随机欺骗用户。
 *
 * 宜忌文案按日期种子轮换：同一天所有人看到同一份黄历，刷新不变。
 */
import type { RecipeItem } from '../../api/recipes'
import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { recordShare } from '../../api/share'
import { fetchRecipePool } from '../../api/recipes'
import { drawAlmanac, FORTUNE_LINES, FORTUNE_NOTES, HOUR_LINES } from '../../data/growth'
import { STATIC_BASE_URL } from '../../utils/assets'
import { saveCookingDraft } from '../../utils/cookingDraft'
import { ensureLogin } from '../../utils/login'

definePage({
  name: 'almanac',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '锅仔老黄历',
  },
})

const router = useRouter()

/* ---- 今日黄历：日期标签 + 宜忌 + 吉时彩蛋 ---- */
const today = new Date()
const dateLabel = `${today.getMonth() + 1}月${today.getDate()}日`
const weekLabel = ['日', '一', '二', '三', '四', '五', '六'][today.getDay()] || ''
const almanac = drawAlmanac(today)
const hourLine = HOUR_LINES[almanac.seed % HOUR_LINES.length] || HOUR_LINES[0]

/* 签支束：收在筒口内、只露出一小截（不再悬在卡片缝隙里） */
const STICKS = [
  { h: 64, r: -8, x: -26 },
  { h: 88, r: -2, x: -8 },
  { h: 76, r: 5, x: 10 },
  { h: 96, r: 12, x: 26 },
  { h: 70, r: 0, x: 40 },
]

/* ---- 摇签状态机：idle → shaking（动画+请求并行） → landed ---- */
const phase = ref<'idle' | 'shaking' | 'landed'>('idle')
const drawRecipe = ref<RecipeItem>()
const drawTier = ref('')
const drawMood = ref('')
const drawLine = ref('')
const drawNote = ref('')
const drawCount = ref(0)

/* 签级 → 荐菜方向：先摇出签，再按签意挑菜（签决定推荐，不是两个独立随机） */
const TIER_MOODS: Record<string, string> = { 上上签: '满足', 吉签: '期待', 平安签: '想家' }

const hint = computed(() => {
  if (phase.value === 'shaking')
    return '锅仔在摇签筒，沙沙响…'
  if (phase.value === 'landed')
    return '签已到手，锅仔念给你听'
  return '今晚不知道吃什么？摇一签'
})

const shareTitle = computed(() => `锅仔老黄历 · 今日宜${almanac.yi[0]}，忌${almanac.ji[0]}`)

async function shake() {
  if (phase.value !== 'idle')
    return
  phase.value = 'shaking'
  drawCount.value += 1
  try {
    // 抽签前静默补登录：未登录用户点进来也能玩，任何失败不阻断 UI
    try {
      await ensureLogin()
    }
    catch {}
    const pool = await fetchRecipePool()
    if (!pool.length)
      throw new Error('empty')
    // 摇签节拍：筒身 1s 跑完 4 次衰减颠簸，请求早就绪，让结果「等」动画而不是动画等结果
    await new Promise(resolve => setTimeout(resolve, 1000))
    // 先摇出签级，再按签意荐菜：签指什么心情，锅仔就挑什么菜（无匹配时回落全池随机）
    const roll = almanac.seed + drawCount.value * 7 + Math.floor(Math.random() * 997)
    const tier = (['上上签', '吉签', '平安签'] as const)[roll % 3] || '吉签'
    const wanted = TIER_MOODS[tier] || '期待'
    const matching = pool.filter(r => (r.moodTags || '').includes(wanted))
    const bag = matching.length ? matching : pool
    const pick = bag[Math.floor(Math.random() * bag.length)]!
    drawRecipe.value = pick
    drawTier.value = tier
    drawMood.value = wanted
    drawLine.value = (FORTUNE_LINES[roll % 3] || FORTUNE_LINES[1]!).replace('{dish}', pick.name)
    drawNote.value = FORTUNE_NOTES[roll % FORTUNE_NOTES.length] || ''
    phase.value = 'landed'
  }
  catch {
    phase.value = 'idle'
    uni.showToast({ title: '签筒今天有点卡，再摇一次', icon: 'none' })
  }
}

/** 签文配图挂了就降级为纯文字签文：绝不留空框破图（与转盘扇区同一兜底原则） */
function onResultImgError() {
  if (drawRecipe.value)
    drawRecipe.value = { ...drawRecipe.value, image: '' }
}

/** 就吃它：与首页转盘/九宫格同链路——存做菜草稿直达做菜页；心情取黄历语境的「期待」而非写死 */
function cookIt() {
  if (!drawRecipe.value)
    return
  saveCookingDraft(drawRecipe.value, '期待')
  router.push({ name: 'cooking' })
}

function reShake() {
  phase.value = 'idle'
  drawRecipe.value = undefined
  drawMood.value = ''
  drawNote.value = ''
}

// 记录分享：发放「分享家」徽章 + 非会员当日 +1 次对话激励（与首页转盘同口径，失败静默）
onShareAppMessage(() => {
  void recordShare('almanac').catch(() => {})
  return { title: shareTitle.value, path: '/pages/almanac/index' }
})
onShareTimeline(() => ({ title: shareTitle.value }))
</script>

<template>
  <view class="alm">
    <wd-navbar title="锅仔老黄历" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <!-- 今日宜忌卡：左宜右忌，双栏同高 -->
    <view class="alm__hero mrc-card">
      <view class="alm__head">
        <text class="alm__eyebrow">
          GUOZAI ALMANAC
        </text>
        <text class="alm__date">
          {{ dateLabel }} · 星期{{ weekLabel }}
        </text>
      </view>
      <view class="alm__cols">
        <view class="alm__col alm__col--yi">
          <view class="alm__col-tag">
            宜
          </view>
          <text v-for="item in almanac.yi" :key="item" class="alm__item">
            {{ item }}
          </text>
        </view>
        <view class="alm__col alm__col--ji">
          <view class="alm__col-tag alm__col-tag--ji">
            忌
          </view>
          <text v-for="item in almanac.ji" :key="item" class="alm__item">
            {{ item }}
          </text>
        </view>
      </view>
      <view class="alm__note">
        <text class="alm__note-main">吉时 · {{ hourLine }}</text>
        <text class="alm__note-sub">黄历每天更新 · 锅仔亲测</text>
      </view>
    </view>

    <!-- 摇签台：签筒拟物（筒盖/红绳/筒口签支/地面投影） + 锅仔押阵 -->
    <view class="alm__draw mrc-card">
      <view class="alm__stage" :class="{ 'is-shaking': phase === 'shaking', 'is-landed': phase === 'landed' }">
        <view class="alm__ground" aria-hidden="true" />
        <view class="alm__tube-wrap">
          <view class="alm__sticks" aria-hidden="true">
            <view
              v-for="(stick, i) in STICKS"
              :key="i"
              class="alm__stick"
              :style="{ height: `${stick.h}rpx`, transform: `rotate(${stick.r}deg) translateX(${stick.x}rpx)` }"
            />
          </view>
          <view class="alm__tube" role="button" :aria-label="phase === 'idle' ? '摇一签' : '签筒'" @click="shake">
            <view class="alm__tube-lid" aria-hidden="true" />
            <view class="alm__tube-band" aria-hidden="true" />
            <view class="alm__tube-text" aria-hidden="true">
              <text>锅</text>
              <text>仔</text>
              <text>签</text>
            </view>
          </view>
          <!-- 被摇出的那支签：landed 时从筒口跳出上抛（CSS 动画，见 alm-draw-stick） -->
          <view class="alm__draw-stick" aria-hidden="true" />
        </view>
        <image class="alm__guozai" :class="{ 'guozai-breathe': phase !== 'shaking' }" :src="`${STATIC_BASE_URL}/static/guozai/action_02_soup.png`" mode="aspectFit" aria-hidden="true" />
      </view>

      <text class="alm__hint" role="status">
        {{ hint }}
      </text>

      <view v-if="phase === 'idle'" class="alm__btn mrc-btn-primary" role="button" aria-label="摇一签定今晚的菜" @click="shake">
        摇一签，定今晚
      </view>

      <!-- 签文：大图菜单卡——签级 + 真菜大图 + 锅仔解读 -->
      <view v-if="phase === 'landed' && drawRecipe" class="alm__result pop-in" role="status">
        <view class="alm__result-hero">
          <image v-if="drawRecipe.image" class="alm__result-img" :src="drawRecipe.image" mode="aspectFill" @error="onResultImgError" />
          <view v-else class="alm__result-img alm__result-img--empty">
            <image :src="`${STATIC_BASE_URL}/static/guozai/action_01_bowl.png`" mode="aspectFit" />
          </view>
          <text class="alm__result-tier">{{ drawTier }}</text>
          <text class="alm__result-name">{{ drawRecipe.name }}</text>
        </view>
        <text class="alm__result-line">
          {{ drawLine }}
        </text>
        <text class="alm__result-read">
          解签 · 此签宜「{{ drawMood }}」，菜随签来
        </text>
        <text v-if="drawNote" class="alm__result-note">
          {{ drawNote }}
        </text>
        <view class="alm__result-actions">
          <view class="alm__action alm__action--primary" role="button" aria-label="就吃这道菜" @click="cookIt">
            就吃它
          </view>
          <view class="alm__action" role="button" aria-label="再摇一签" @click="reShake">
            再摇一签
          </view>
        </view>
      </view>
    </view>

    <view class="alm__foot">
      <text>黄历是锅仔写的，写的时候刚吃饱</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* finesse · register=h5(小程序页) · 沿用 mrc token 体系 · SOUL=7 SPECTACLE=4(衰减摇签节拍+出签上抛) DENSITY=5 */
.alm {
  min-height: 100vh;
  box-sizing: border-box;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(80rpx + env(safe-area-inset-bottom));
}

/* ---- 宜忌卡 ---- */
.alm__hero { margin-top: 20rpx; }
.alm__head { display: flex; flex-direction: column; gap: 6rpx; margin-bottom: 24rpx; }
.alm__eyebrow { color: var(--mrc-accent); font-size: 18rpx; font-weight: 800; letter-spacing: 3rpx; }
.alm__date { color: var(--mrc-text-strong); font-size: 38rpx; font-weight: 800; }

.alm__cols { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 20rpx; }
.alm__col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  padding: 24rpx 16rpx;
  border-radius: var(--mrc-radius-md);
  box-sizing: border-box;
}
.alm__col--yi { background: var(--mrc-surface-mint); }
.alm__col--ji { background: var(--mrc-surface-2); }
.alm__col-tag {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: var(--mrc-mint);
  color: #fff;
  font-size: 32rpx;
  font-weight: 800;
  box-shadow: 0 8rpx 16rpx rgba(40, 194, 160, 0.28);
}
.alm__col-tag--ji { background: var(--mrc-brown); box-shadow: 0 8rpx 16rpx rgba(196, 149, 106, 0.28); }
.alm__item { position: relative; padding-left: 20rpx; color: var(--mrc-text); font-size: 26rpx; font-weight: 600; text-align: center; }
.alm__item::before { content: ''; position: absolute; left: 2rpx; top: 50%; width: 8rpx; height: 8rpx; margin-top: -4rpx; border-radius: 50%; background: currentColor; opacity: .45; }
.alm__note { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 6rpx 14rpx; margin-top: 22rpx; }
.alm__note-main { padding: 6rpx 22rpx; border-radius: 999rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 22rpx; font-weight: 800; }
.alm__note-sub { color: var(--mrc-text-light); font-size: 20rpx; }

/* ---- 摇签台（拟物细化：筒盖/红绳箍/签支束/地面投影） ---- */
.alm__draw { margin-top: 28rpx; display: flex; flex-direction: column; align-items: center; }
.alm__stage { position: relative; display: flex; align-items: flex-end; justify-content: center; gap: 40rpx; padding: 44rpx 20rpx 26rpx; }
.alm__ground { position: absolute; bottom: 6rpx; left: 50%; width: 400rpx; height: 40rpx; margin-left: -200rpx; border-radius: 50%; background: radial-gradient(closest-side, rgba(120, 60, 30, 0.14), transparent); }
.alm__tube-wrap { position: relative; }
.alm__tube {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 176rpx;
  height: 236rpx;
  padding-bottom: 14rpx;
  border-radius: 26rpx 26rpx 62rpx 62rpx;
  background: linear-gradient(160deg, #D08A5F, #9C5A34);
  box-shadow: inset 0 8rpx 16rpx rgba(255, 255, 255, 0.28), inset -12rpx 0 20rpx rgba(90, 40, 15, 0.24), 0 16rpx 30rpx rgba(120, 60, 30, 0.3);
}
.alm__tube:active { transform: scale(0.96); }
.alm__tube-lid {
  position: absolute;
  top: -16rpx;
  left: 50%;
  z-index: 3;
  width: 198rpx;
  height: 38rpx;
  margin-left: -99rpx;
  border-radius: 50%;
  background: linear-gradient(160deg, #E3A476, #B07043);
  box-shadow: 0 6rpx 12rpx rgba(90, 40, 15, 0.28), inset 0 5rpx 8rpx rgba(255, 255, 255, 0.32);
}
.alm__tube-band {
  position: absolute;
  bottom: 44rpx;
  left: 0;
  z-index: 3;
  width: 100%;
  height: 14rpx;
  background: rgba(198, 60, 40, 0.5);
  box-shadow: 0 2rpx 4rpx rgba(90, 40, 15, 0.2);
}
.alm__tube-text {
  position: relative;
  z-index: 4;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  padding: 12rpx 16rpx;
  border-radius: 12rpx;
  background: linear-gradient(160deg, #C63C28, #A62E1E);
  color: #FFF3E0;
  font-size: 21rpx;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 2rpx;
  box-shadow: inset 0 2rpx 6rpx rgba(255, 255, 255, 0.28), 0 4rpx 8rpx rgba(90, 40, 15, 0.24);
}
.alm__sticks { position: absolute; bottom: 208rpx; left: 50%; z-index: 0; width: 0; }
.alm__stick {
  position: absolute;
  bottom: 0;
  width: 13rpx;
  border-radius: 7rpx 7rpx 0 0;
  background: linear-gradient(#F7E7C8, #E0C093);
  box-shadow: 0 4rpx 8rpx rgba(120, 60, 30, 0.18);
  transform-origin: bottom center;
}
.alm__stick::after { content: ''; position: absolute; top: 0; left: 0; width: 13rpx; height: 18rpx; border-radius: 7rpx 7rpx 0 0; background: #C63C28; }
.alm__guozai { width: 140rpx; height: 140rpx; margin-bottom: 14rpx; }

/* ---- 摇签动效（1s 单次完整节拍，与 JS 1000ms 等待同步）：
   筒身 4 次衰减颠簸 + 倾角交替，签支错峰沙沙，筒盖抖动，
   地面投影随颠簸呼吸，锅仔前倾看热闹 —— 物理感来自衰减，不是匀速循环 ---- */
.alm__stage.is-shaking .alm__tube { animation: alm-shake 1s cubic-bezier(0.36, 0.07, 0.19, 0.97) 1 both; }
@keyframes alm-shake {
  0% { transform: translate3d(0, 0, 0) rotate(0); }
  8% { transform: translate3d(0, -30rpx, 0) rotate(-7deg); }
  18% { transform: translate3d(0, 0, 0) rotate(6deg); }
  28% { transform: translate3d(0, -22rpx, 0) rotate(-5deg); }
  38% { transform: translate3d(0, 0, 0) rotate(4deg); }
  48% { transform: translate3d(0, -13rpx, 0) rotate(-3deg); }
  58% { transform: translate3d(0, 0, 0) rotate(2deg); }
  66% { transform: translate3d(0, -6rpx, 0) rotate(-1deg); }
  74%, 100% { transform: translate3d(0, 0, 0) rotate(0); }
}
.alm__stage.is-shaking .alm__stick { animation: alm-stick 1s ease-out 1 both; }
.alm__stage.is-shaking .alm__stick:nth-child(2) { animation-delay: 0.06s; }
.alm__stage.is-shaking .alm__stick:nth-child(3) { animation-delay: 0.12s; }
.alm__stage.is-shaking .alm__stick:nth-child(4) { animation-delay: 0.03s; }
.alm__stage.is-shaking .alm__stick:nth-child(5) { animation-delay: 0.09s; }
@keyframes alm-stick {
  0%, 74%, 100% { transform: translateY(0); }
  8% { transform: translateY(-20rpx); }
  18% { transform: translateY(2rpx); }
  28% { transform: translateY(-14rpx); }
  38% { transform: translateY(0); }
  48% { transform: translateY(-9rpx); }
  58% { transform: translateY(0); }
  66% { transform: translateY(-4rpx); }
}
.alm__stage.is-shaking .alm__tube-lid { animation: alm-lid 1s ease-out 1 both; }
@keyframes alm-lid {
  0%, 74%, 100% { transform: translateY(0); }
  8% { transform: translateY(-6rpx) rotate(-2deg); }
  28% { transform: translateY(-4rpx) rotate(2deg); }
  48% { transform: translateY(-2rpx); }
}
.alm__stage.is-shaking .alm__ground { animation: alm-ground 1s ease-out 1 both; }
@keyframes alm-ground {
  0%, 18%, 38%, 58%, 74%, 100% { transform: scale(1); opacity: 1; }
  8% { transform: scale(0.82); opacity: 0.7; }
  28% { transform: scale(0.86); opacity: 0.75; }
  48% { transform: scale(0.9); opacity: 0.8; }
  66% { transform: scale(0.95); opacity: 0.85; }
}
.alm__stage.is-shaking .alm__guozai { animation: alm-watch 1s ease-in-out 1 both; }
@keyframes alm-watch {
  0%, 100% { transform: rotate(0); }
  20% { transform: rotate(-10deg) translateX(-6rpx); }
  55% { transform: rotate(8deg) translateX(4rpx); }
}

/* ---- 出签：被摇中的那支从筒口跳出上抛，签文卡随后弹入 ---- */
.alm__draw-stick {
  position: absolute;
  bottom: 240rpx;
  left: 50%;
  z-index: 5;
  width: 16rpx;
  height: 110rpx;
  margin-left: -8rpx;
  border-radius: 8rpx;
  background: linear-gradient(#F7E7C8, #E0C093);
  box-shadow: 0 4rpx 10rpx rgba(120, 60, 30, 0.25);
  opacity: 0;
  transform-origin: bottom center;
}
.alm__draw-stick::after { content: ''; position: absolute; top: 0; left: 0; width: 100%; height: 26rpx; border-radius: 8rpx 8rpx 0 0; background: #C63C28; }
.alm__stage.is-landed .alm__draw-stick { animation: alm-draw-stick 0.75s cubic-bezier(0.22, 0.9, 0.32, 1.1) 0.05s both; }
@keyframes alm-draw-stick {
  0% { opacity: 0; transform: translateY(36rpx) rotate(-16deg) scale(0.7); }
  22% { opacity: 1; transform: translateY(-36rpx) rotate(5deg) scale(1); }
  55% { opacity: 1; transform: translateY(-140rpx) rotate(12deg); }
  100% { opacity: 0; transform: translateY(-230rpx) rotate(18deg); }
}

.alm__hint { min-height: 36rpx; margin-top: 26rpx; color: var(--mrc-text-sub); font-size: 24rpx; }
.alm__btn { width: 100%; margin-top: 20rpx; }

/* ---- 签文结果 ---- */
.alm__result {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  width: 100%;
  margin-top: 24rpx;
  padding: 30rpx 24rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: var(--mrc-radius-lg);
  background: var(--mrc-surface-sun);
  box-sizing: border-box;
  /* 出签先飞（0.05–0.8s），签文卡等签落到手再弹入（delay .55s） */
  animation: alm-pop 0.38s cubic-bezier(0.34, 1.56, 0.64, 1) 0.55s backwards;
}
@keyframes alm-pop {
  from { opacity: 0; transform: translateY(26rpx) scale(0.94); }
  to { opacity: 1; transform: none; }
}
.alm__result-tier {
  position: absolute;
  top: 18rpx;
  left: 18rpx;
  z-index: 2;
  padding: 6rpx 26rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.92);
  color: var(--mrc-accent);
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
  box-shadow: 0 4rpx 12rpx rgba(61, 37, 25, 0.18);
}
/* 大图主视觉：签文卡即菜单卡，菜是绝对主角 */
.alm__result-hero { position: relative; width: 100%; height: 300rpx; border-radius: var(--mrc-radius-md); overflow: hidden; }
.alm__result-img { width: 100%; height: 100%; }
.alm__result-img--empty { display: flex; align-items: center; justify-content: center; background: var(--mrc-surface-2); }
.alm__result-img--empty image { width: 140rpx; height: 140rpx; opacity: .8; }
.alm__result-hero::after { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg, transparent 46%, rgba(61, 37, 25, 0.78)); }
.alm__result-name {
  position: absolute;
  bottom: 16rpx;
  left: 22rpx;
  z-index: 2;
  max-width: calc(100% - 44rpx);
  color: #FFFFFF;
  font-size: 42rpx;
  font-weight: 900;
  letter-spacing: 1rpx;
  text-shadow: 0 2rpx 10rpx rgba(61, 37, 25, 0.5);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.alm__result-line { color: var(--mrc-text); font-size: 24rpx; line-height: 1.6; text-align: center; }
.alm__result-note { color: var(--mrc-text-light); font-size: 21rpx; line-height: 1.5; text-align: center; }
.alm__result-read { padding: 4rpx 20rpx; border-radius: 999rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 20rpx; font-weight: 700; }
.alm__result-actions { display: flex; gap: 18rpx; margin-top: 8rpx; width: 100%; }
.alm__action {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: var(--mrc-radius-pill);
  background: var(--mrc-surface-2);
  color: var(--mrc-text-deep);
  font-size: 26rpx;
  font-weight: 700;
}
.alm__action:active { transform: scale(0.96); }
.alm__action--primary { background: var(--mrc-primary-grad); color: #fff; box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); }

.alm__foot { display: flex; justify-content: center; margin-top: 36rpx; }
.alm__foot text { color: var(--mrc-text-light); font-size: 20rpx; }

@media (prefers-reduced-motion: reduce) {
  .alm__stage.is-shaking .alm__tube,
  .alm__stage.is-shaking .alm__stick,
  .alm__stage.is-shaking .alm__tube-lid,
  .alm__stage.is-shaking .alm__ground,
  .alm__stage.is-shaking .alm__guozai,
  .alm__stage.is-landed .alm__draw-stick,
  .alm__guozai.guozai-breathe,
  .alm__result.pop-in {
    animation: none;
  }
  /* 静态终端态：签文卡直接呈现，不依赖入场动画 */
  .alm__result { animation: none; }
}
</style>
