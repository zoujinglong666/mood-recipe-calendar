<script setup lang="ts">
import { ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import GuozaiButton from '@/components/guozai/GuozaiButton.vue'
import GuozaiChipGroup from '@/components/guozai/GuozaiChipGroup.vue'
import GuozaiWotButton from '@/components/guozai/GuozaiWotButton.vue'

definePage({ name: 'component-demo', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '组件 Demo' } })

const people = ref('2')
const dishes = ref('2')
const cuisines = ref<string[]>(['川菜'])
const spice = ref('NORMAL')

const PEOPLE = [
  { value: '1', label: '1 人' },
  { value: '2', label: '2 人' },
  { value: '3', label: '3 人' },
  { value: '4', label: '4 人' },
]
const DISHES = [
  { value: '1', label: '1 道' },
  { value: '2', label: '2 道' },
  { value: '3', label: '3 道' },
]
const SPICE = [
  { value: 'NONE', label: '不吃辣' },
  { value: 'MILD', label: '微辣' },
  { value: 'NORMAL', label: '正常辣' },
  { value: 'HOT', label: '很能吃辣' },
]
const CUISINES = ['川菜', '湘菜', '粤菜', '江浙菜', '东北菜', '西北菜', '云贵菜', '日韩料理'].map(item => ({ value: item, label: item }))
</script>

<template>
  <view class="demo-page">
    <wd-navbar title="组件 Demo" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <!-- 1. 还原图中那块：简单周菜单 -->
    <view class="demo-section">
      <text class="demo-section__title">
        手动选好，生成简单周菜单
      </text>
      <text class="demo-section__hint">
        锅仔会按人数、做饭天数和菜数，避开你的忌口，整理出一份基础菜单和买菜清单。
      </text>

      <view class="demo-field">
        <text class="demo-field__label">几个人吃</text>
        <GuozaiChipGroup v-model="people" :options="PEOPLE" :columns="4" aria-label="选择人数" />
      </view>
      <view class="demo-field">
        <text class="demo-field__label">每天几道菜</text>
        <GuozaiChipGroup v-model="dishes" :options="DISHES" :columns="3" aria-label="选择每天菜数" />
      </view>

      <GuozaiButton variant="primary" aria-label="生成菜单">生成本周简单菜单</GuozaiButton>

      <view class="demo-divider" />
      <text class="demo-section__title demo-section__title--sm">开通会员，交给锅仔来安排</text>
      <text class="demo-section__hint">每日 20 次首页推荐，专享对话式周菜单和随时重排。</text>
      <GuozaiButton variant="dark" aria-label="查看会员权益">查看会员权益</GuozaiButton>
    </view>

    <!-- 2. 选择器变体 -->
    <view class="demo-section">
      <text class="demo-section__title">选择器变体</text>
      <text class="demo-section__hint">单选 · 4 列</text>
      <GuozaiChipGroup v-model="spice" :options="SPICE" :columns="4" aria-label="辣度" />
      <text class="demo-section__hint demo-section__hint--gap">单选 · 2 列</text>
      <GuozaiChipGroup v-model="spice" :options="SPICE" :columns="2" aria-label="辣度" />
      <text class="demo-section__hint demo-section__hint--gap">多选 · 自适应换行居中</text>
      <GuozaiChipGroup v-model="cuisines" :options="CUISINES" multiple aria-label="菜系" />
      <text class="demo-section__hint demo-section__hint--gap">禁用态</text>
      <GuozaiChipGroup v-model="dishes" :options="DISHES" :columns="3" disabled aria-label="菜数禁用" />
    </view>

    <!-- 3. 按钮变体 -->
    <view class="demo-section">
      <text class="demo-section__title">按钮变体</text>
      <GuozaiButton variant="primary" aria-label="主按钮">主按钮 primary</GuozaiButton>
      <GuozaiButton variant="dark" aria-label="深棕按钮">深棕按钮 dark</GuozaiButton>
      <GuozaiButton variant="secondary" aria-label="次级按钮">次级按钮 secondary</GuozaiButton>
      <GuozaiButton variant="ghost" aria-label="危险按钮">清除全部口味记忆</GuozaiButton>

      <text class="demo-section__hint demo-section__hint--gap">禁用 / 加载中 / 非通栏</text>
      <GuozaiButton variant="primary" disabled aria-label="禁用">禁用 disabled</GuozaiButton>
      <GuozaiButton variant="primary" loading aria-label="加载中">加载中</GuozaiButton>
      <view class="demo-row">
        <GuozaiButton variant="secondary" :block="false" aria-label="取消">取消</GuozaiButton>
        <GuozaiButton variant="primary" :block="false" aria-label="确定">确定</GuozaiButton>
      </view>
    </view>

    <!-- 4. wot 官方按钮（搭配） -->
    <view class="demo-section">
      <text class="demo-section__title">wot 按钮（搭配）</text>
      <text class="demo-section__hint">已把 wot 默认蓝色主色换成锅仔珊瑚色，可与上面的原生按钮混用</text>
      <GuozaiWotButton block round size="large" aria-label="wot 主按钮">主按钮 · coral</GuozaiWotButton>

      <text class="demo-section__hint demo-section__hint--gap">两套并排对比</text>
      <view class="demo-row">
        <GuozaiButton variant="primary" :block="false" aria-label="原生主按钮">原生按钮</GuozaiButton>
        <GuozaiWotButton size="large" round aria-label="wot 主按钮">wot 按钮</GuozaiWotButton>
      </view>

      <text class="demo-section__hint demo-section__hint--gap">变体</text>
      <view class="demo-row demo-row--wrap">
        <GuozaiWotButton size="small" variant="base">base</GuozaiWotButton>
        <GuozaiWotButton size="small" variant="plain">plain</GuozaiWotButton>
        <GuozaiWotButton size="small" variant="soft">soft</GuozaiWotButton>
        <GuozaiWotButton size="small" variant="subtle">subtle</GuozaiWotButton>
        <GuozaiWotButton size="small" variant="text">text</GuozaiWotButton>
        <GuozaiWotButton size="small" variant="dashed">dashed</GuozaiWotButton>
      </view>

      <text class="demo-section__hint demo-section__hint--gap">尺寸</text>
      <view class="demo-row demo-row--wrap">
        <GuozaiWotButton size="mini">mini</GuozaiWotButton>
        <GuozaiWotButton size="small">small</GuozaiWotButton>
        <GuozaiWotButton size="medium">medium</GuozaiWotButton>
        <GuozaiWotButton size="large">large</GuozaiWotButton>
      </view>

      <text class="demo-section__hint demo-section__hint--gap">类型 / 状态</text>
      <view class="demo-row demo-row--wrap">
        <GuozaiWotButton type="success" size="small">success</GuozaiWotButton>
        <GuozaiWotButton type="warning" size="small">warning</GuozaiWotButton>
        <GuozaiWotButton type="danger" size="small">danger</GuozaiWotButton>
        <GuozaiWotButton size="small" disabled>disabled</GuozaiWotButton>
        <GuozaiWotButton size="small" loading>loading</GuozaiWotButton>
        <GuozaiWotButton size="small" icon="download">图标</GuozaiWotButton>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.demo-page { min-height: 100vh; box-sizing: border-box; padding: 0 32rpx calc(56rpx + env(safe-area-inset-bottom)); background: var(--mrc-bg); }
.demo-section { padding: 28rpx; margin-bottom: 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft); }
.demo-section__title, .demo-section__hint { display: block; }
.demo-section__title { color: var(--mrc-text-deep); font-size: 31rpx; font-weight: 800; }
.demo-section__title--sm { margin-top: 4rpx; font-size: 27rpx; }
.demo-section__hint { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 23rpx; line-height: 1.55; }
.demo-section__hint--gap { margin-top: 24rpx; }
.demo-field { margin-top: 22rpx; }
.demo-field__label { display: block; margin-bottom: 12rpx; color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 700; }
.demo-divider { height: 2rpx; margin: 26rpx 0; background: var(--mrc-border-light); }
/* 非通栏按钮行：按内容宽度排布，不拉伸（通栏按钮自带 width:100%） */
.demo-row { display: flex; align-items: center; gap: 14rpx; margin-top: 14rpx; }
.demo-row > .gz-btn,
.demo-row > .wd-button { flex: 0 0 auto; }
.demo-row--wrap { flex-wrap: wrap; }
</style>
