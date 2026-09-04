<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { ref, computed } from 'vue'
import Icon from '../../components/common/Icon.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import { recommendRecipe, requestDeepRecipe, type RecipeItem } from '../../api/recipes'
import { createVirtualOrder, fetchVirtualProducts, getVirtualPaymentParams, requestWechatVirtualPayment, type VirtualProduct } from '../../api/virtualCommerce'
import { ensureLogin } from '../../utils/login'

definePage({
  name: 'recipe',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '今日推荐',
  },
})

const route = useRoute()
const router = useRouter()

const mood = computed(() => (route.query.mood as string) || '开心')

const HEALING_TEXTS: Record<string, string> = {
  开心: '开心的时候，吃什么都香！',
  平静: '平静的日子，需要一道温柔的菜。',
  疲惫: '疲惫的时候，一碗热汤比任何话都暖。',
  焦虑: '焦虑的时候，好好吃顿饭，世界会慢下来。',
  难过: '难过的时候，食物是最好的安慰。',
  嘴馋: '嘴馋的时候，就放纵自己一次吧！',
  低落: '低落的时候，一口热乎的就能治愈。',
  想家: '想家的时候，做一道家乡的味道。',
}

const loading = ref(true)
const error = ref('')
const recipe = ref<RecipeItem | null>(null)
const showSteps = ref(false)
const showAiPanel = ref(false)
const deepIngredients = ref('')
const deepMinutes = ref('30')
const deepPreference = ref('')
const deepLoading = ref(false)
const productsLoading = ref(false)
const purchasingSku = ref('')
const aiProducts = ref<VirtualProduct[]>([])

const ingredients = computed(() => {
  if (!recipe.value?.ingredients) return []
  try { return JSON.parse(recipe.value.ingredients) } catch { return [] }
})
const steps = computed(() => {
  if (!recipe.value?.steps) return []
  try { return JSON.parse(recipe.value.steps) } catch { return [] }
})
const healingText = computed(() => HEALING_TEXTS[mood.value] || recipe.value?.description || '好好吃饭，天天开心。')

async function loadRecipe() {
  loading.value = true
  error.value = ''
  try {
    recipe.value = await recommendRecipe(mood.value)
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onLoad(() => {
  loadRecipe()
})

function toggleSteps() { showSteps.value = !showSteps.value }
function goRecord() {
  if (!recipe.value) return
  // switchTab 不支持 query，用 storage 暂存菜名与心情，记录页 onShow 消费
  uni.setStorageSync('mrc_record_draft', { dish: recipe.value.name, mood: mood.value })
  router.pushTab({ name: 'record' })
}
function goBuy() { openAiPanel() }
function onShare() {
  // #ifdef MP-WEIXIN
  ;(uni as any).showShareMenu({ menus: ['shareAppMessage', 'shareTimeline'] })
  uni.showToast({ title: '可以从右上角分享给好友', icon: 'none' })
  // #endif
  // #ifndef MP-WEIXIN
  uni.showToast({ title: '请在微信小程序中分享给好友', icon: 'none' })
  // #endif
}

async function openAiPanel() {
  showAiPanel.value = true
  if (aiProducts.value.length || productsLoading.value) return
  productsLoading.value = true
  try {
    aiProducts.value = await fetchVirtualProducts()
  } catch (e: any) {
    uni.showToast({ title: e.message || '权益加载失败，请稍后重试', icon: 'none' })
  } finally {
    productsLoading.value = false
  }
}

async function requestPersonalMenu() {
  deepLoading.value = true
  try {
    const openid = await ensureLogin()
    recipe.value = await requestDeepRecipe({
      openid,
      mood: mood.value,
      ingredients: deepIngredients.value.trim(),
      maxMinutes: deepMinutes.value.trim(),
      preference: deepPreference.value.trim(),
    })
    showAiPanel.value = false
    showSteps.value = false
    uni.showToast({ title: '锅仔为你做好专属菜单啦', icon: 'success' })
  } catch (e: any) {
    const message = e.message || '生成失败，请稍后重试'
    if (message.includes('解锁')) {
      uni.showToast({ title: '先解锁私人菜单，就能按食材定制', icon: 'none' })
    } else {
      uni.showToast({ title: message, icon: 'none' })
    }
  } finally {
    deepLoading.value = false
  }
}

async function purchase(product: VirtualProduct) {
  purchasingSku.value = product.sku
  try {
    const openid = await ensureLogin()
    const order = await createVirtualOrder(openid, product.sku)
    const params = await getVirtualPaymentParams(openid, order.orderNo)
    await requestWechatVirtualPayment(params)
    uni.showToast({ title: '支付已提交，权益到账后即可使用', icon: 'none' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '暂时无法发起支付', icon: 'none' })
  } finally {
    purchasingSku.value = ''
  }
}
</script>

<template>
  <view class="recipe-page">
    <wd-navbar title="AI 今日推荐" left-arrow safe-area-inset-top @click-left="navBack"  custom-style="background-color: transparent !important;" />

    <view class="recipe-page__share" @click="onShare">
      <Icon name="share" :size="36" color="var(--mrc-text)" />
    </view>

    <!-- Loading -->
    <LoadingState v-if="loading" text="锅仔正在挑菜..." />

    <!-- Error -->
    <ErrorState v-else-if="error" :text="error" @retry="loadRecipe" />

    <!-- 内容 -->
    <template v-else-if="recipe">
      <text class="recipe-kicker">锅仔 AI 为你配的这一餐</text>
      <text class="recipe-healing">{{ healingText }}</text>

      <view class="ai-entry" @click="openAiPanel">
        <image class="ai-entry__img" src="/static/guozai/action_10_thinking.png" mode="aspectFit" />
        <view class="ai-entry__main">
          <text class="ai-entry__title">让锅仔按你的食材来配菜</text>
          <text class="ai-entry__sub">说说家里有什么、想吃多快、有哪些忌口</text>
        </view>
        <text class="ai-entry__arrow">›</text>
      </view>

      <view class="recipe-hero">
        <image class="recipe-hero__img guozai-breathe" src="/static/guozai/action_02_soup.png" mode="aspectFit" />
      </view>

      <view class="recipe-card">
        <image class="recipe-card__img" :src="recipe.image || '/static/dish_tomato_beef.png'" mode="aspectFill" />
        <view class="recipe-card__info">
          <text class="recipe-card__name">{{ recipe.name }}</text>
          <text class="recipe-card__desc">{{ recipe.description }}</text>
        </view>
        <view class="recipe-card__meta">
          <view class="recipe-card__time">
            <Icon name="clock" :size="28" color="var(--mrc-text-deep)" />
            <text>{{ recipe.cookingTime }}分钟</text>
          </view>
          <view class="recipe-card__tag">{{ recipe.difficulty }}</view>
        </view>
      </view>

      <view class="recipe-section">
        <text class="recipe-section__title">食材清单</text>
        <view class="recipe-ingredients">
          <view v-for="(ing, i) in ingredients" :key="i" class="recipe-ingredients__item">{{ ing }}</view>
        </view>
      </view>

      <view class="recipe-section">
        <view class="recipe-section__head">
          <text class="recipe-section__title">完整做法</text>
          <text class="recipe-section__toggle" @click="toggleSteps">{{ showSteps ? '收起' : '展开' }}</text>
        </view>
        <view v-if="showSteps" class="recipe-steps">
          <view v-for="(step, i) in steps" :key="i" class="recipe-steps__item">
            <text class="recipe-steps__num">{{ Number(i) + 1 }}</text>
            <text class="recipe-steps__text">{{ step }}</text>
          </view>
        </view>
      </view>

      <view class="recipe-bottom">
        <view class="recipe-btn recipe-btn--primary" @click="goBuy">
          <Icon name="cart" :size="36" color="#fff" />
          <text>定制这一餐</text>
        </view>
        <view class="recipe-btn recipe-btn--ghost" @click="goRecord">
          <text>我做了这道菜</text>
          <Icon name="camera" :size="36" color="var(--mrc-text-deep)" />
        </view>
      </view>

      <view v-if="showAiPanel" class="ai-mask" @click.self="showAiPanel = false">
        <view class="ai-sheet">
          <view class="ai-sheet__head">
            <view>
              <text class="ai-sheet__eyebrow">锅仔 AI 私人菜单</text>
              <text class="ai-sheet__title">今天想怎么吃？</text>
            </view>
            <text class="ai-sheet__close" @click="showAiPanel = false">×</text>
          </view>
          <view class="ai-field">
            <text>已有食材</text>
            <input v-model="deepIngredients" placeholder="例如：鸡蛋、番茄、面条" :maxlength="80" />
          </view>
          <view class="ai-field">
            <text>最多花几分钟</text>
            <input v-model="deepMinutes" type="number" placeholder="例如：30" :maxlength="3" />
          </view>
          <view class="ai-field">
            <text>口味与忌口</text>
            <input v-model="deepPreference" placeholder="例如：少辣、不吃香菜" :maxlength="80" />
          </view>
          <view class="ai-sheet__cta" :class="{ 'ai-sheet__cta--loading': deepLoading }" @click="requestPersonalMenu">
            {{ deepLoading ? '锅仔正在思考…' : '生成我的专属菜单' }}
          </view>
          <view class="ai-products">
            <text class="ai-products__title">还没有权益？先解锁</text>
            <view v-if="productsLoading" class="ai-products__hint">正在加载可用权益…</view>
            <view v-for="product in aiProducts" :key="product.sku" class="ai-product">
              <view class="ai-product__main">
                <text class="ai-product__name">{{ product.title }}</text>
                <text class="ai-product__desc">{{ product.description }}</text>
              </view>
              <view class="ai-product__buy" @click="purchase(product)">
                {{ purchasingSku === product.sku ? '发起中' : `¥${(product.priceFen / 100).toFixed(2)}` }}
              </view>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.recipe-page {
  min-height: 100vh;
  background: var(--mrc-bg);
  padding: 0 32rpx;
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  position: relative;
}
/* 分享按钮：navbar 右侧被小程序胶囊遮挡，移到内容区右上角浮动 */
.recipe-page__share { position: absolute; top: calc(env(safe-area-inset-top) + 92rpx); right: 24rpx; z-index: 50; width: 72rpx; height: 72rpx; border-radius: 50%; background: rgba(255, 255, 255, 0.9); box-shadow: var(--mrc-shadow-sm); display: flex; align-items: center; justify-content: center; }
.recipe-healing {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  line-height: 1.5;
  margin: 16rpx 8rpx 12rpx;
  letter-spacing: 1rpx;
}
.recipe-kicker {
  display: block;
  margin: 16rpx 8rpx 4rpx;
  color: var(--mrc-accent);
  font-size: 21rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}
.ai-entry {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 16rpx 0 24rpx;
  padding: 16rpx 20rpx;
  background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach));
  border: 2rpx solid var(--mrc-border);
  border-radius: 28rpx;
  box-shadow: var(--mrc-shadow-soft);
}
.ai-entry:active { transform: scale(0.98); }
.ai-entry__img { width: 84rpx; height: 84rpx; flex-shrink: 0; }
.ai-entry__main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.ai-entry__title { font-size: 28rpx; font-weight: 800; color: var(--mrc-text-deep); }
.ai-entry__sub { font-size: 22rpx; color: var(--mrc-text-sub); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ai-entry__arrow { font-size: 42rpx; color: var(--mrc-accent); }
.recipe-hero {
  position: relative;
  display: flex;
  justify-content: center;
  height: 224rpx;
  margin-bottom: 20rpx;
  overflow: hidden;
  border-radius: 32rpx;
  background: var(--mrc-surface-peach);
  border: 2rpx solid var(--mrc-border-light);
}
.recipe-hero__img {
  width: 310rpx;
  height: 270rpx;
  margin-top: 4rpx;
}
.recipe-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  padding: 24rpx;
  margin-bottom: 32rpx;
  box-shadow: var(--mrc-shadow);
}
.recipe-card__img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
}
.recipe-card__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}
.recipe-card__name {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
}
.recipe-card__desc {
  font-size: 26rpx;
  color: var(--mrc-text-sub);
}
.recipe-card__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
  flex-shrink: 0;
}
.recipe-card__time {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 26rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.recipe-card__tag {
  font-size: 22rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-surface-peach);
  padding: 8rpx 16rpx;
  border-radius: 20rpx;
}
.recipe-section {
  margin-bottom: 32rpx;
}
.recipe-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.recipe-section__title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  padding-left: 16rpx;
  border-left: 8rpx solid var(--mrc-primary);
}
.recipe-section__toggle {
  font-size: 26rpx;
  color: var(--mrc-text-sub);
}
.recipe-ingredients {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.recipe-ingredients__item {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
  color: var(--mrc-text-deep);
}
.recipe-steps {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.recipe-steps__item {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
}
.recipe-steps__num {
  width: 40rpx;
  height: 40rpx;
  background: var(--mrc-primary);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.recipe-steps__text {
  font-size: 28rpx;
  color: var(--mrc-text-deep);
  line-height: 1.6;
  flex: 1;
}
.recipe-bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
  background: rgba(248, 236, 218, 0.96);
  border-top: 2rpx solid var(--mrc-border);
  z-index: 30;
}
.recipe-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 96rpx;
  border-radius: 48rpx;
  font-size: 30rpx;
  font-weight: 600;
}
.recipe-btn--primary {
  background: var(--mrc-primary-grad);
  color: #fff;
  box-shadow: var(--mrc-shadow-coral);
}
.recipe-btn--ghost {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border);
  color: var(--mrc-text-deep);
}
.recipe-btn:active {
  transform: scale(0.97);
}
.ai-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: flex-end;
  background: rgba(54, 35, 22, 0.48);
}
.ai-sheet {
  width: 100%;
  max-height: 86vh;
  overflow-y: auto;
  box-sizing: border-box;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  border-radius: 40rpx 40rpx 0 0;
  background: var(--mrc-surface);
}
.ai-sheet__head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 28rpx; }
.ai-sheet__eyebrow { display: block; color: var(--mrc-accent); font-size: 21rpx; font-weight: 800; letter-spacing: 2rpx; }
.ai-sheet__title { display: block; margin-top: 8rpx; color: var(--mrc-text-deep); font-size: 42rpx; font-weight: 800; }
.ai-sheet__close { width: 64rpx; height: 64rpx; line-height: 58rpx; text-align: center; font-size: 52rpx; color: var(--mrc-text-sub); }
.ai-field { display: flex; flex-direction: column; gap: 12rpx; margin-bottom: 20rpx; }
.ai-field text { color: var(--mrc-text-deep); font-size: 26rpx; font-weight: 700; }
.ai-field input { height: 82rpx; padding: 0 24rpx; box-sizing: border-box; border: 2rpx solid var(--mrc-border-light); border-radius: 20rpx; color: var(--mrc-text-deep); font-size: 27rpx; background: var(--mrc-bg); }
.ai-sheet__cta { display: flex; align-items: center; justify-content: center; height: 94rpx; margin: 28rpx 0 32rpx; border-radius: 47rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 30rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); }
.ai-sheet__cta--loading { opacity: 0.65; }
.ai-products { padding-top: 24rpx; border-top: 2rpx solid var(--mrc-border-light); }
.ai-products__title { display: block; margin-bottom: 16rpx; color: var(--mrc-text-deep); font-size: 28rpx; font-weight: 800; }
.ai-products__hint { color: var(--mrc-text-sub); font-size: 25rpx; }
.ai-product { display: flex; align-items: center; gap: 16rpx; padding: 20rpx 0; border-bottom: 2rpx solid var(--mrc-border-light); }
.ai-product__main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8rpx; }
.ai-product__name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 700; }
.ai-product__desc { color: var(--mrc-text-sub); font-size: 21rpx; line-height: 1.45; }
.ai-product__buy { min-width: 118rpx; padding: 16rpx 12rpx; border-radius: 32rpx; text-align: center; background: var(--mrc-surface-sun); color: var(--mrc-accent); font-size: 25rpx; font-weight: 800; }
</style>
