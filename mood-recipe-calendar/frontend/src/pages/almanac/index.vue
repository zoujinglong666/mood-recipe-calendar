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
import { drawAlmanac, FORTUNE_LINES } from '../../data/growth'
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

/* ---- 今日黄历：日期标签 + 宜忌 ---- */
const today = new Date()
const dateLabel = `${today.getMonth() + 1}月${today.getDate()}日`
const weekLabel = ['日', '一', '二', '三', '四', '五', '六'][today.getDay()] || ''
const almanac = drawAlmanac(today)

/* ---- 摇签状态机：idle → shaking（动画+请求并行） → landed ---- */
const phase = ref<'idle' | 'shaking' | 'landed'>('idle')
const drawRecipe = ref<RecipeItem>()
const drawTier = ref('')
const drawLine = ref('')
const drawCount = ref(0)

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
    // 摇签节拍：签筒先响 ~0.9s，请求早就绪，让结果「等」动画而不是动画等结果
    await new Promise(resolve => setTimeout(resolve, 900))
    const pick = pool[Math.floor(Math.random() * pool.length)]!
    const roll = almanac.seed + drawCount.value * 7 + Math.floor(Math.random() * 997)
    drawRecipe.value = pick
    drawTier.value = ['上上签', '吉签', '平安签'][roll % 3] || '吉签'
    drawLine.value = (FORTUNE_LINES[roll % 3] || FORTUNE_LINES[1]!).replace('{dish}', pick.name)
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
        <text>黄历每天更新，锅仔开火亲测有效</text>
      </view>
    </view>

    <!-- 摇签台：签筒拟物 + 锅仔押阵 -->
    <view class="alm__draw mrc-card">
      <view class="alm__stage" :class="{ 'is-shaking': phase === 'shaking' }">
        <view class="alm__sticks" aria-hidden="true">
          <view v-for="i in 3" :key="i" class="alm__stick" :style="{ transform: `rotate(${(i - 2) * 8}deg) translateY(${i === 2 ? -12 : 0}rpx)` }" />
        </view>
        <view class="alm__tube" role="button" :aria-label="phase === 'idle' ? '摇一签' : '签筒'" @click="shake">
          <text class="alm__tube-text">
            锅仔签
          </text>
        </view>
        <image class="alm__guozai" :class="{ 'guozai-breathe': phase !== 'shaking' }" :src="`${STATIC_BASE_URL}/static/guozai/action_02_soup.png`" mode="aspectFit" aria-hidden="true" />
      </view>

      <text class="alm__hint" role="status">
        {{ hint }}
      </text>

      <view v-if="phase === 'idle'" class="alm__btn mrc-btn-primary" role="button" aria-label="摇一签定今晚的菜" @click="shake">
        摇一签，定今晚
      </view>

      <!-- 签文：签级 + 真菜 + 锅仔解读 -->
      <view v-if="phase === 'landed' && drawRecipe" class="alm__result pop-in" role="status">
        <text class="alm__result-tier">
          {{ drawTier }}
        </text>
        <view class="alm__result-dish">
          <image v-if="drawRecipe.image" class="alm__result-img" :src="drawRecipe.image" mode="aspectFill" @error="onResultImgError" />
          <text class="alm__result-name">
            {{ drawRecipe.name }}
          </text>
        </view>
        <text class="alm__result-line">
          {{ drawLine }}
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
/* finesse · register=h5(小程序页) · 沿用 mrc token 体系 · SOUL=7 SPECTACLE=3 DENSITY=5 */
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
.alm__item { color: var(--mrc-text); font-size: 26rpx; font-weight: 600; text-align: center; }
.alm__note { display: flex; justify-content: center; margin-top: 22rpx; }
.alm__note text { color: var(--mrc-text-light); font-size: 20rpx; }

/* ---- 摇签台 ---- */
.alm__draw { margin-top: 28rpx; display: flex; flex-direction: column; align-items: center; }
.alm__stage { position: relative; display: flex; align-items: flex-end; justify-content: center; gap: 30rpx; padding-top: 70rpx; }
.alm__tube {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 150rpx;
  height: 190rpx;
  padding-bottom: 18rpx;
  border-radius: 24rpx 24rpx 44rpx 44rpx;
  background: linear-gradient(160deg, #C9805A, #9C5A34);
  box-shadow: inset 0 6rpx 14rpx rgba(255, 255, 255, 0.25), 0 14rpx 28rpx rgba(120, 60, 30, 0.28);
}
.alm__tube:active { transform: scale(0.96); }
.alm__tube-text { color: #FFF6E8; font-size: 22rpx; font-weight: 800; letter-spacing: 2rpx; }
.alm__sticks { position: absolute; top: -46rpx; left: 50%; display: flex; gap: 12rpx; margin-left: -40rpx; }
.alm__stick {
  width: 10rpx;
  height: 110rpx;
  border-radius: 6rpx;
  background: linear-gradient(#F7E7C8, #E0C093);
  box-shadow: 0 4rpx 8rpx rgba(120, 60, 30, 0.18);
  transform-origin: bottom center;
}
.alm__guozai { width: 130rpx; height: 130rpx; margin-bottom: 10rpx; }

.alm__stage.is-shaking .alm__tube { animation: alm-shake 0.32s ease-in-out infinite; }
@keyframes alm-shake {
  0%, 100% { transform: rotate(0); }
  30% { transform: rotate(-6deg); }
  70% { transform: rotate(6deg); }
}
.alm__stage.is-shaking .alm__stick { animation: alm-stick 0.32s ease-in-out infinite; }
@keyframes alm-stick {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10rpx); }
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
}
.alm__result-tier {
  padding: 6rpx 26rpx;
  border-radius: 999rpx;
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
  font-size: 24rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
}
.alm__result-dish { display: flex; align-items: center; gap: 18rpx; min-width: 0; }
.alm__result-img { width: 96rpx; height: 96rpx; flex: 0 0 96rpx; overflow: hidden; border-radius: var(--mrc-radius-md); background: var(--mrc-surface-2); }
.alm__result-name {
  color: var(--mrc-text-strong);
  font-size: 36rpx;
  font-weight: 800;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.alm__result-line { color: var(--mrc-text); font-size: 24rpx; line-height: 1.6; text-align: center; }
.alm__result-actions { display: flex; gap: 18rpx; margin-top: 8rpx; }
.alm__action {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 84rpx;
  padding: 0 44rpx;
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
  .alm__guozai.guozai-breathe,
  .alm__result.pop-in {
    animation: none;
  }
}
</style>
