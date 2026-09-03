<script setup lang="ts">
import { ref, computed } from 'vue'
import AppNav from '../../components/common/AppNav.vue'
import Icon from '../../components/common/Icon.vue'
import LoadingState from '../../components/guozai/LoadingState.vue'
import ErrorState from '../../components/guozai/ErrorState.vue'
import { recommendRecipe, type RecipeItem } from '../../api/recipes'

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
  router.push({ name: 'record', query: { dish: recipe.value.name, mood: mood.value } })
}
function goBuy() { uni.showToast({ title: '跳转买菜平台', icon: 'none' }) }
function onShare() { uni.showToast({ title: '分享功能', icon: 'none' }) }
</script>

<template>
  <view class="recipe-page">
    <AppNav title="AI 今日推荐" right-icon="share" @nav-right="onShare" />

    <!-- Loading -->
    <LoadingState v-if="loading" text="锅仔正在挑菜..." />

    <!-- Error -->
    <ErrorState v-else-if="error" :text="error" @retry="loadRecipe" />

    <!-- 内容 -->
    <template v-else-if="recipe">
      <text class="recipe-kicker">锅仔 AI 为你配的这一餐</text>
      <text class="recipe-healing">{{ healingText }}</text>

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
          <text>一键购食材</text>
        </view>
        <view class="recipe-btn recipe-btn--ghost" @click="goRecord">
          <text>我做了这道菜</text>
          <Icon name="camera" :size="36" color="var(--mrc-text-deep)" />
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
}
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
  gap: 10rpx;
  flex-shrink: 0;
}
.recipe-card__time {
  display: flex;
  align-items: center;
  gap: 6rpx;
  font-size: 26rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.recipe-card__tag {
  font-size: 22rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-surface-peach);
  padding: 6rpx 16rpx;
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
  padding-left: 14rpx;
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
  gap: 10rpx;
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
</style>
