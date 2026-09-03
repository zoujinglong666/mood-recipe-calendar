<template>
  <view class="recipe-card">
    <!-- 菜品图 -->
    <view class="recipe-card__img">
      <text v-if="!recipe.image" class="recipe-card__emoji">🍲</text>
      <image v-else :src="recipe.image" mode="aspectFill" class="recipe-card__img-src" />
      <view v-if="recipe.moodTags?.length" class="recipe-card__tag">
        {{ recipe.moodTags[0] }}
      </view>
    </view>

    <!-- 信息 -->
    <view class="recipe-card__info">
      <text class="recipe-card__name">{{ recipe.name }}</text>
      <text v-if="recipe.description" class="recipe-card__desc">{{ recipe.description }}</text>
      <view class="recipe-card__meta">
        <text class="recipe-card__meta-item">⏱ {{ recipe.cookingTime }}分钟</text>
        <text class="recipe-card__meta-item">{{ recipe.difficulty }}难度</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
export interface Recipe {
  id?: string
  name: string
  description?: string
  image?: string
  cookingTime?: number
  difficulty?: string
  ingredients?: string[]
  moodTags?: string[]
}

defineProps<{
  recipe: Recipe
}>()
</script>

<style lang="scss" scoped>
.recipe-card {
  background: var(--mrc-white);
  border-radius: var(--mrc-radius-lg);
  overflow: hidden;
  box-shadow: 0 6rpx 20rpx rgba(232, 131, 107, 0.12);
}
.recipe-card__img {
  position: relative;
  height: 300rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--mrc-bg-card);
}
.recipe-card__img-src {
  width: 100%;
  height: 100%;
}
.recipe-card__emoji {
  font-size: 120rpx;
}
.recipe-card__tag {
  position: absolute;
  left: 20rpx;
  top: 20rpx;
  background: rgba(255, 255, 255, 0.92);
  color: var(--mrc-accent);
  font-size: 22rpx;
  padding: 6rpx 18rpx;
  border-radius: 50rpx;
}
.recipe-card__info {
  padding: 24rpx;
}
.recipe-card__name {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: var(--mrc-text);
}
.recipe-card__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--mrc-text-sub);
}
.recipe-card__meta {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
}
.recipe-card__meta-item {
  font-size: 24rpx;
  color: var(--mrc-accent);
}
</style>
