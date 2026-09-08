<script setup lang="ts">
import type { RecipeFeedbackAction, RecipeItem } from '../../api/recipes'
import type { VirtualProduct } from '../../api/virtualCommerce'
import { computed, nextTick, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { recommendRecipe, requestDeepRecipe, sendRecipeFeedback } from '../../api/recipes'
import { createVirtualOrder, fetchVirtualOrder, fetchVirtualProducts, getVirtualPaymentParams, requestWechatVirtualPayment } from '../../api/virtualCommerce'
import Icon from '../../components/common/Icon.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import { ensureLogin } from '../../utils/login'
import { toast, toastError, toastSuccess } from '../../utils/toast'

definePage({ name: 'recipe', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '今日推荐' } })

const route = useRoute()
const router = useRouter()
const mood = computed(() => (route.query.mood as string) || '开心')
const HEALING_TEXTS: Record<string, string> = {
  开心: '你今天的好心情，适合配一口热乎又满足的。',
  平静: '不赶时间的这一餐，就让味道慢慢展开。',
  疲惫: '今天辛苦了，选一道省心又暖胃的给你。',
  焦虑: '先把注意力交给锅里升起的香气，慢慢来。',
  难过: '不用急着振作，先认真吃一顿温暖的饭。',
  嘴馋: '既然想吃点好的，就选一道香气很有存在感的。',
  低落: '热饭会稳稳接住今天的你，先吃饱再说。',
  想家: '熟悉的家常味最会安慰人，这道很适合今天。',
  期待: '把期待放进锅里，今晚值得一顿有仪式感的饭。',
  满足: '此刻刚刚好，用一道舒服的菜延续这份满足。',
  得意: '今天这么棒，当然要用一道拿手菜奖励自己。',
  害羞: '不用说很多，让一顿认真做的饭替你表达。',
}

const loading = ref(false)
const error = ref('')
const recipe = ref<RecipeItem | null>(null)
const imageFailed = ref(false)
const showSteps = ref(false)
const showAiPanel = ref(false)
const deepIngredients = ref('')
const deepMinutes = ref('30')
const deepPreference = ref('')
const deepLoading = ref(false)
const productsLoading = ref(false)
const productsLoaded = ref(false)
const productsError = ref('')
const purchasingSku = ref('')
const aiProducts = ref<VirtualProduct[]>([])
const feedbackLoading = ref<RecipeFeedbackAction | ''>('')
const likedRecipeId = ref<number | null>(null)

function parseStringList(value?: string): string[] {
  if (!value)
    return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map(String).filter(Boolean) : []
  }
  catch {
    return []
  }
}

const ingredients = computed(() => parseStringList(recipe.value?.ingredients))
const steps = computed(() => parseStringList(recipe.value?.steps))
const feedbackAvailable = computed(() => Number(recipe.value?.id) > 0)
const recipeImageAvailable = computed(() => Boolean(recipe.value?.image) && !imageFailed.value)
const healingText = computed(() => recipe.value?.recommendationReason?.trim() || HEALING_TEXTS[mood.value] || recipe.value?.description || '好好吃饭，锅仔会陪你慢慢找到喜欢的味道。')
const primaryText = computed(() => showSteps.value || !steps.value.length ? '做完了，记一笔' : '查看完整做法')

function readableError(errorValue: unknown, fallback: string) {
  const message = errorValue instanceof Error ? errorValue.message : String((errorValue as any)?.message || '')
  return !message || /request:fail|network|timeout/i.test(message) ? fallback : message
}

async function loadRecipe() {
  if (loading.value)
    return
  loading.value = true
  error.value = ''
  imageFailed.value = false
  showSteps.value = false
  try {
    recipe.value = await recommendRecipe(mood.value) || null
  }
  catch (e: any) {
    error.value = readableError(e, '网络开小差了')
  }
  finally {
    loading.value = false
  }
}

onLoad(loadRecipe)

async function revealSteps() {
  showSteps.value = true
  await nextTick()
  uni.pageScrollTo({ selector: '#recipe-steps', duration: 240 })
}

function toggleSteps() {
  if (showSteps.value)
    showSteps.value = false
  else revealSteps()
}

function handlePrimaryAction() {
  if (!showSteps.value && steps.value.length)
    revealSteps()
  else goRecord()
}

function goRecord() {
  if (!recipe.value)
    return
  uni.setStorageSync('mrc_record_draft', { dish: recipe.value.name, mood: mood.value, recipeId: recipe.value.id })
  router.pushTab({ name: 'record' })
}

async function sendFeedback(action: RecipeFeedbackAction) {
  if (!recipe.value?.id || feedbackLoading.value || (action === 'LIKE' && likedRecipeId.value === recipe.value.id))
    return
  feedbackLoading.value = action
  try {
    await ensureLogin()
    await sendRecipeFeedback(recipe.value.id, action)
    if (action === 'LIKE') {
      likedRecipeId.value = recipe.value.id
      toast('锅仔记住啦，以后多推荐这类菜')
    }
    else {
      toast('明白，锅仔换一道更合胃口的')
      await loadRecipe()
    }
  }
  catch (e: any) {
    toastError(e, '记录偏好失败，请重试')
  }
  finally {
    feedbackLoading.value = ''
  }
}

function onShare() {
  // #ifdef MP-WEIXIN
  ;(uni as any).showShareMenu({ menus: ['shareAppMessage', 'shareTimeline'] })
  toast('可以从右上角分享给好友')
  // #endif
  // #ifndef MP-WEIXIN
  toast('分享功能请在微信小程序中使用')
  // #endif
}

async function openAiPanel() {
  showAiPanel.value = true
  if (!productsLoaded.value && !productsLoading.value)
    await loadProducts()
}

async function loadProducts() {
  productsLoading.value = true
  productsError.value = ''
  try {
    aiProducts.value = await fetchVirtualProducts()
    productsLoaded.value = true
  }
  catch (e: any) {
    productsError.value = e?.message || '权益加载失败'
  }
  finally {
    productsLoading.value = false
  }
}

async function requestPersonalMenu() {
  if (deepLoading.value)
    return
  deepLoading.value = true
  try {
    const openid = await ensureLogin()
    recipe.value = await requestDeepRecipe({ openid, mood: mood.value, ingredients: deepIngredients.value.trim(), maxMinutes: deepMinutes.value.trim(), preference: deepPreference.value.trim() })
    imageFailed.value = false
    showAiPanel.value = false
    showSteps.value = false
    toastSuccess('锅仔为你做好专属菜单啦')
  }
  catch (e: any) {
    const message = e?.message || '生成失败，请稍后重试'
    toast(message.includes('解锁') ? '先解锁私人菜单，就能按食材定制' : message)
  }
  finally {
    deepLoading.value = false
  }
}

async function purchase(product: VirtualProduct) {
  if (purchasingSku.value)
    return
  purchasingSku.value = product.sku
  let checkingDelivery = false
  try {
    const openid = await ensureLogin()
    const order = await createVirtualOrder(openid, product.sku)
    const params = await getVirtualPaymentParams(openid, order.orderNo)
    await requestWechatVirtualPayment(params)
    checkingDelivery = true
    uni.showLoading({ title: '锅仔正在确认权益…', mask: true })
    const delivered = await waitForDelivery(order.orderNo)
    toast(delivered ? '权益已到账，可以定制菜单啦' : '支付已完成，权益确认中')
  }
  catch (e: any) {
    toastError(e, '暂时无法发起支付')
  }
  finally {
    if (checkingDelivery)
      uni.hideLoading()
    purchasingSku.value = ''
  }
}

async function waitForDelivery(orderNo: string) {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await new Promise(resolve => setTimeout(resolve, attempt === 0 ? 900 : 1600))
    if ((await fetchVirtualOrder(orderNo)).status === 'DELIVERED')
      return true
  }
  return false
}
</script>

<template>
  <view class="recipe-page">
    <wd-navbar title="AI 今日推荐" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />
    <view v-if="loading" class="recipe-state" aria-label="锅仔正在推荐菜谱">
      <view class="recipe-state__media">
        <LoadingState text="锅仔正在看你的心情和口味…" />
      </view>
      <view class="recipe-state__line recipe-state__line--title" />
      <view class="recipe-state__line" />
    </view>
    <view v-else-if="error" class="recipe-state recipe-state--center">
      <ErrorState :text="error" subtext="网络恢复后，锅仔会接着为你挑菜" @retry="loadRecipe" />
    </view>

    <template v-else-if="recipe">
      <view class="recipe-content">
        <view class="recommend-card">
          <view class="recommend-card__topline">
            <view class="mood-chip">
              <text class="mood-chip__dot" /><text>今天有点{{ mood }}</text>
            </view>
            <view class="recommend-card__share pressable" role="button" aria-label="分享今日推荐" @click="onShare">
              <Icon name="share" :size="36" color="#EF5A3C" />
            </view>
          </view>
          <view class="dish-copy">
            <text class="dish-copy__name">
              {{ recipe.name }}
            </text>
            <view class="dish-meta" aria-label="菜谱信息">
              <view class="dish-meta__item">
                <Icon name="clock" :size="30" color="#EF5A3C" /><text>{{ recipe.cookingTime || '--' }} 分钟</text>
              </view>
              <view class="dish-meta__divider" />
              <view class="dish-meta__item">
                <Icon name="flame" :size="30" color="#EF5A3C" /><text>{{ recipe.difficulty || '家常难度' }}</text>
              </view>
            </view>
          </view>
          <view class="dish-media">
            <image v-if="recipeImageAvailable" class="dish-media__image" :src="recipe.image" mode="aspectFill" aria-label="推荐菜品图片" @error="imageFailed = true" />
            <view v-else class="dish-media__fallback">
              <image src="/static/guozai/action_01_bowl.png" mode="aspectFit" />
            </view>
            <view class="guozai-note">
              <image class="guozai-note__avatar guozai-breathe" src="/static/guozai/action_16_chopsticks.png" mode="aspectFit" />
              <view class="guozai-note__bubble">
                <text class="guozai-note__label">
                  锅仔为什么推荐它
                </text>
                <text class="guozai-note__text">
                  {{ healingText }}
                </text>
              </view>
            </view>
          </view>
          <text v-if="recipe.description" class="dish-description">
            {{ recipe.description }}
          </text>
        </view>

        <view v-if="feedbackAvailable" class="feedback-row" aria-label="告诉锅仔这道菜是否合胃口">
          <view class="feedback-action pressable" :class="{ 'is-disabled': feedbackLoading || likedRecipeId === recipe.id }" role="button" :aria-label="likedRecipeId === recipe.id ? '已喜欢这道菜' : '喜欢这道菜'" @click="sendFeedback('LIKE')">
            <Icon name="heart" :size="30" color="#EF5A3C" /><text>{{ feedbackLoading === 'LIKE' ? '记住中…' : likedRecipeId === recipe.id ? '已经记住' : '喜欢这道' }}</text>
          </view>
          <view class="feedback-action pressable" :class="{ 'is-disabled': Boolean(feedbackLoading) }" role="button" aria-label="不想吃这道菜，换一道推荐" @click="sendFeedback('DISLIKE')">
            <Icon name="dice" :size="30" color="#A1826A" /><text>{{ feedbackLoading === 'DISLIKE' ? '换菜中…' : '不想吃，换一道' }}</text>
          </view>
        </view>

        <view class="custom-entry pressable" role="button" aria-label="打开按食材定制菜单" @click="openAiPanel">
          <image class="custom-entry__image" src="/static/guozai/action_10_thinking.png" mode="aspectFit" />
          <view class="custom-entry__copy">
            <text class="custom-entry__title">
              家里有现成食材？
            </text><text class="custom-entry__subtitle">
              让锅仔按时间、口味和忌口重新配菜
            </text>
          </view>
          <text class="custom-entry__link">
            去定制
          </text>
        </view>

        <view class="recipe-section">
          <view class="section-heading">
            <text class="section-heading__eyebrow">
              PREPARE
            </text><text class="section-heading__title">
              食材清单
            </text>
          </view>
          <view v-if="ingredients.length" class="ingredient-list">
            <view v-for="(ingredient, index) in ingredients" :key="`${ingredient}-${index}`" class="ingredient-item">
              <text class="ingredient-item__dot" /><text>{{ ingredient }}</text>
            </view>
          </view>
          <view v-else class="content-empty">
            锅仔还没拿到食材清单，先看看做法吧。
          </view>
        </view>

        <view id="recipe-steps" class="recipe-section recipe-section--steps">
          <view class="section-heading section-heading--row">
            <view>
              <text class="section-heading__eyebrow">
                COOK
              </text><text class="section-heading__title">
                完整做法
              </text>
            </view>
            <view v-if="steps.length" class="section-toggle pressable" role="button" :aria-label="showSteps ? '收起完整做法' : '展开完整做法'" @click="toggleSteps">
              {{ showSteps ? '收起' : `查看 ${steps.length} 步` }}
            </view>
          </view>
          <view v-if="showSteps && steps.length" class="step-list">
            <view v-for="(step, index) in steps" :key="index" class="step-item">
              <text class="step-item__number">
                {{ Number(index) + 1 }}
              </text><text class="step-item__text">
                {{ step }}
              </text>
            </view>
          </view>
          <view v-else-if="steps.length" class="steps-preview">
            准备好后再展开，跟着锅仔一步步做。
          </view>
          <view v-else class="content-empty">
            这道菜暂时没有详细步骤，你仍然可以记录自己的做法。
          </view>
        </view>
      </view>

      <view class="primary-bar">
        <view class="primary-bar__inner">
          <view class="primary-action pressable" role="button" :aria-label="primaryText" @click="handlePrimaryAction">
            <Icon :name="showSteps || !steps.length ? 'camera' : 'book'" :size="36" color="#fff" /><text>{{ primaryText }}</text>
          </view>
        </view>
      </view>

      <view v-if="showAiPanel" class="ai-mask" @click.self="showAiPanel = false">
        <view class="ai-sheet" role="dialog" aria-label="锅仔 AI 私人菜单">
          <view class="ai-sheet__head">
            <view>
              <text class="ai-sheet__eyebrow">
                锅仔 AI 私人菜单
              </text><text class="ai-sheet__title">
                家里有什么，就做什么
              </text><text class="ai-sheet__subtitle">
                下面都可以不填，锅仔会按你的心情自由发挥。
              </text>
            </view>
            <view class="ai-sheet__close pressable" role="button" aria-label="关闭私人菜单" @click="showAiPanel = false">
              ×
            </view>
          </view>
          <label class="ai-field"><text class="ai-field__label">已有食材</text><input v-model="deepIngredients" placeholder="例如：鸡蛋、番茄、面条" :maxlength="80"></label>
          <label class="ai-field"><text class="ai-field__label">最多花几分钟</text><input v-model="deepMinutes" type="number" placeholder="例如：30" :maxlength="3"></label>
          <label class="ai-field"><text class="ai-field__label">口味与忌口</text><input v-model="deepPreference" placeholder="例如：少辣、不吃香菜" :maxlength="80"></label>
          <view class="ai-sheet__cta pressable" :class="{ 'is-disabled': deepLoading }" role="button" :aria-label="deepLoading ? '专属菜单生成中' : '生成专属菜单'" @click="requestPersonalMenu">
            {{ deepLoading ? '锅仔正在组合食材…' : '生成我的专属菜单' }}
          </view>
          <view class="ai-products">
            <text class="ai-products__title">
              私人菜单权益
            </text><text class="ai-products__intro">
              权益不足时可在这里解锁，基础推荐始终可以免费使用。
            </text>
            <view v-if="productsLoading" class="ai-products__state">
              正在加载可用权益…
            </view>
            <view v-else-if="productsError" class="ai-products__state ai-products__state--error">
              <text>{{ productsError }}</text><view class="ai-products__retry pressable" role="button" aria-label="重新加载权益" @click="loadProducts">
                重试
              </view>
            </view>
            <view v-else-if="productsLoaded && !aiProducts.length" class="ai-products__state">
              暂时没有可购买权益，请稍后再来。
            </view>
            <view v-for="product in aiProducts" :key="product.sku" class="ai-product">
              <view class="ai-product__main">
                <text class="ai-product__name">
                  {{ product.title }}
                </text><text class="ai-product__desc">
                  {{ product.description }}
                </text>
              </view>
              <view class="ai-product__buy pressable" :class="{ 'is-disabled': Boolean(purchasingSku) }" role="button" :aria-label="`购买${product.title}`" @click="purchase(product)">
                {{ purchasingSku === product.sku ? '处理中…' : `¥${(product.priceFen / 100).toFixed(2)}` }}
              </view>
            </view>
          </view>
        </view>
      </view>
    </template>

    <view v-else class="recipe-state recipe-state--center">
      <ErrorState text="锅仔今天没挑到合适的菜" subtext="换个心情再试一次，或稍后回来看看" action-text="重新推荐" @retry="loadRecipe" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.recipe-page { min-height: 100vh; min-height: 100dvh; box-sizing: border-box; overflow-x: hidden; color: var(--mrc-text); background: radial-gradient(90% 36% at 8% 4%, var(--mrc-surface-sun) 0%, transparent 72%), var(--mrc-bg); }
.recipe-content, .recipe-state { width: calc(100% - 48rpx); max-width: 820rpx; margin: 0 auto; box-sizing: border-box; }
.recipe-content { padding: 18rpx 0 calc(180rpx + env(safe-area-inset-bottom)); }
.pressable { transition: transform 180ms ease, opacity 180ms ease, background-color 180ms ease; }
.pressable:active { transform: scale(.97); }
.is-disabled { opacity: .52; pointer-events: none; }
.recipe-state { min-height: 840rpx; padding: 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 36rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.recipe-state--center { display: flex; align-items: center; justify-content: center; }
.recipe-state__media { height: 560rpx; overflow: hidden; border-radius: 28rpx; background: var(--mrc-surface-2); }
.recipe-state__line { height: 24rpx; margin-top: 20rpx; border-radius: 12rpx; background: var(--mrc-surface-2); }
.recipe-state__line--title { width: 58%; height: 42rpx; margin-top: 28rpx; }
.recipe-state :deep(.gz-error__btn) { display: flex; align-items: center; justify-content: center; min-height: 88rpx; box-sizing: border-box; }
.recommend-card { overflow: hidden; border: 2rpx solid var(--mrc-border-light); border-radius: 40rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss); }
.recommend-card__topline { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; min-height: 88rpx; padding: 0 28rpx; }
.mood-chip { display: inline-flex; align-items: center; gap: 12rpx; color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 700; }
.mood-chip__dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--mrc-pop); box-shadow: 0 0 0 8rpx var(--mrc-surface-sun); }
.recommend-card__share { display: flex; align-items: center; justify-content: center; width: 88rpx; height: 88rpx; margin-right: -20rpx; border-radius: 50%; }
.dish-media { position: relative; width: 100%; height: 0; padding-bottom: 75%; overflow: hidden; background: var(--mrc-surface-peach); }
.dish-media__image { position: absolute; inset: 0; width: 100%; height: 100%; }
.dish-media__fallback { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--mrc-text-sub); }
.dish-media__fallback image { width: 260rpx; height: 220rpx; transform: translateY(-36rpx); }
.dish-copy { padding: 16rpx 28rpx 26rpx; }
.dish-copy__name { display: block; color: var(--mrc-text-strong); font-size: 48rpx; font-weight: 800; line-height: 1.25; }
.dish-meta { display: flex; align-items: center; gap: 20rpx; margin-top: 16rpx; }
.dish-meta__item { display: flex; align-items: center; gap: 8rpx; color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 700; }
.dish-meta__divider { width: 2rpx; height: 28rpx; background: var(--mrc-border); }
.guozai-note { position: absolute; right: 18rpx; bottom: 18rpx; left: 18rpx; display: flex; align-items: flex-end; gap: 10rpx; }
.guozai-note__avatar { width: 96rpx; height: 96rpx; flex-shrink: 0; }
.guozai-note__bubble { flex: 1; min-width: 0; padding: 16rpx 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 22rpx 22rpx 22rpx 8rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); }
.guozai-note__label { display: block; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 1rpx; }
.guozai-note__text { display: block; margin-top: 6rpx; color: var(--mrc-text-deep); font-size: 24rpx; line-height: 1.45; }
.dish-description { display: block; padding: 20rpx 28rpx 26rpx; color: var(--mrc-text-sub); font-size: 25rpx; line-height: 1.6; }
.feedback-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; margin-top: 20rpx; }
.feedback-action { display: flex; align-items: center; justify-content: center; gap: 10rpx; min-height: 88rpx; padding: 0 16rpx; box-sizing: border-box; border: 2rpx solid var(--mrc-border); border-radius: 44rpx; color: var(--mrc-text-deep); background: var(--mrc-surface); font-size: 24rpx; font-weight: 700; }
.custom-entry { display: flex; align-items: center; gap: 16rpx; min-height: 112rpx; margin-top: 28rpx; padding: 12rpx 20rpx; box-sizing: border-box; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface-2); }
.custom-entry__image { width: 84rpx; height: 84rpx; flex-shrink: 0; }
.custom-entry__copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 6rpx; }
.custom-entry__title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.custom-entry__subtitle { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.45; }
.custom-entry__link { flex-shrink: 0; color: var(--mrc-accent); font-size: 24rpx; font-weight: 800; }
.recipe-section { margin-top: 44rpx; }
.section-heading { margin-bottom: 20rpx; }
.section-heading--row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.section-heading__eyebrow { display: block; color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 3rpx; }
.section-heading__title { display: block; margin-top: 6rpx; color: var(--mrc-text-strong); font-size: 36rpx; font-weight: 800; }
.section-toggle { display: flex; align-items: center; justify-content: center; min-width: 136rpx; min-height: 88rpx; padding: 0 20rpx; box-sizing: border-box; border-radius: 44rpx; color: var(--mrc-accent); background: var(--mrc-surface); font-size: 24rpx; font-weight: 700; }
.ingredient-list { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }
.ingredient-item { display: flex; align-items: center; gap: 14rpx; min-height: 80rpx; padding: 12rpx 20rpx; box-sizing: border-box; border: 2rpx solid var(--mrc-border-light); border-radius: 20rpx; color: var(--mrc-text-deep); background: var(--mrc-surface); font-size: 26rpx; line-height: 1.45; }
.ingredient-item__dot { width: 10rpx; height: 10rpx; flex-shrink: 0; border-radius: 50%; background: var(--mrc-primary-deep); }
.content-empty, .steps-preview { padding: 28rpx; border: 2rpx dashed var(--mrc-border); border-radius: 24rpx; color: var(--mrc-text-sub); background: var(--mrc-surface); font-size: 25rpx; line-height: 1.6; }
.step-list { display: flex; flex-direction: column; gap: 16rpx; }
.step-item { display: flex; align-items: flex-start; gap: 18rpx; padding: 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface); }
.step-item__number { display: flex; align-items: center; justify-content: center; width: 48rpx; height: 48rpx; flex-shrink: 0; border-radius: 50%; color: #fff; background: var(--mrc-primary-deep); font-size: 23rpx; font-weight: 800; }
.step-item__text { flex: 1; color: var(--mrc-text-deep); font-size: 27rpx; line-height: 1.7; }
.primary-bar { position: fixed; right: 0; bottom: 0; left: 0; z-index: 30; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); border-top: 2rpx solid var(--mrc-border-light); background: var(--mrc-surface); box-shadow: 0 -8rpx 24rpx rgba(40, 24, 16, .08); }
.primary-bar__inner { max-width: 820rpx; margin: 0 auto; }
.primary-action { display: flex; align-items: center; justify-content: center; gap: 12rpx; min-height: 96rpx; border-radius: 48rpx; color: #fff; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral); font-size: 30rpx; font-weight: 800; letter-spacing: 1rpx; }
.ai-mask { position: fixed; inset: 0; z-index: 60; display: flex; align-items: flex-end; background: rgba(24, 15, 10, .58); }
.ai-sheet { width: 100%; max-height: 88vh; max-height: 88dvh; overflow-y: auto; box-sizing: border-box; padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom)); border-radius: 40rpx 40rpx 0 0; background: var(--mrc-surface); }
.ai-sheet__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; margin-bottom: 28rpx; }
.ai-sheet__eyebrow { display: block; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.ai-sheet__title { display: block; margin-top: 8rpx; color: var(--mrc-text-strong); font-size: 38rpx; font-weight: 800; line-height: 1.35; }
.ai-sheet__subtitle { display: block; margin-top: 10rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.5; }
.ai-sheet__close { display: flex; align-items: center; justify-content: center; width: 88rpx; height: 88rpx; flex-shrink: 0; border-radius: 50%; color: var(--mrc-text-sub); background: var(--mrc-surface-2); font-size: 48rpx; line-height: 1; }
.ai-field { display: flex; flex-direction: column; gap: 10rpx; margin-bottom: 20rpx; }
.ai-field__label { color: var(--mrc-text-deep); font-size: 26rpx; font-weight: 800; }
.ai-field input { height: 88rpx; padding: 0 22rpx; box-sizing: border-box; border: 2rpx solid var(--mrc-border); border-radius: 20rpx; color: var(--mrc-text-deep); background: var(--mrc-bg); font-size: 27rpx; }
.ai-sheet__cta { display: flex; align-items: center; justify-content: center; min-height: 96rpx; margin: 28rpx 0 32rpx; border-radius: 48rpx; color: #fff; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral); font-size: 29rpx; font-weight: 800; }
.ai-products { padding-top: 28rpx; border-top: 2rpx solid var(--mrc-border-light); }
.ai-products__title { display: block; color: var(--mrc-text-deep); font-size: 29rpx; font-weight: 800; }
.ai-products__intro { display: block; margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.5; }
.ai-products__state { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; min-height: 88rpx; margin-top: 18rpx; color: var(--mrc-text-sub); font-size: 24rpx; }
.ai-products__state--error { color: var(--mrc-accent); }
.ai-products__retry { display: flex; align-items: center; justify-content: center; min-width: 100rpx; min-height: 88rpx; color: var(--mrc-accent); font-weight: 800; }
.ai-product { display: flex; align-items: center; gap: 16rpx; min-height: 112rpx; border-bottom: 2rpx solid var(--mrc-border-light); }
.ai-product__main { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 6rpx; }
.ai-product__name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }
.ai-product__desc { color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.45; }
.ai-product__buy { display: flex; align-items: center; justify-content: center; min-width: 132rpx; min-height: 88rpx; padding: 0 16rpx; box-sizing: border-box; border-radius: 44rpx; color: var(--mrc-accent); background: var(--mrc-surface-sun); font-size: 25rpx; font-weight: 800; }
@media (max-width: 350px) { .recipe-content, .recipe-state { width: calc(100% - 32rpx); } .dish-copy__name { font-size: 42rpx; } .feedback-row, .ingredient-list { grid-template-columns: 1fr; } .custom-entry__subtitle { display: none; } }
@media (min-width: 500px) { .dish-media { padding-bottom: 56%; } }
@media (min-width: 720px), (orientation: landscape) and (min-width: 640px) { .recipe-content, .recipe-state { max-width: 900rpx; } .dish-media { padding-bottom: 52%; } }
@media (prefers-reduced-motion: reduce) { .pressable { transition: none; } .pressable:active { transform: none; } }
</style>
