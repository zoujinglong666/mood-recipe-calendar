<script setup lang="ts">
import type { Feedback, FeedbackCategory } from '../../api/feedback'
import { computed, ref } from 'vue'
import { navBack } from '@/composables/useNavBar'
import { fetchMyFeedback, submitFeedback } from '../../api/feedback'

definePage({
  name: 'feedback',
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '反馈建议',
  },
})

const categories: FeedbackCategory[] = ['功能建议', '体验问题', '内容反馈', '其他']
const statusLabels: Record<string, string> = {
  OPEN: '已收到',
  PROCESSING: '处理中',
  RESOLVED: '已完成',
  CLOSED: '已关闭',
}

const category = ref<FeedbackCategory>('功能建议')
const content = ref('')
const contact = ref('')
const sending = ref(false)
const history = ref<Feedback[]>([])
const canSubmit = computed(() => content.value.trim().length > 0 && !sending.value)

function statusLabel(status: string) {
  return statusLabels[status] || '已收到'
}

function formatDate(value: string) {
  if (!value)
    return ''
  return value.replace('T', ' ').slice(0, 16)
}

async function loadHistory() {
  try {
    history.value = await fetchMyFeedback()
  }
  catch {
    // 历史记录加载失败不阻塞用户继续提交反馈。
  }
}

async function submit() {
  if (sending.value)
    return
  if (!content.value.trim()) {
    uni.showToast({ title: '写下你的建议吧', icon: 'none' })
    return
  }

  sending.value = true
  try {
    await submitFeedback({
      category: category.value,
      content: content.value.trim(),
      contact: contact.value.trim() || undefined,
    })
    content.value = ''
    contact.value = ''
    await loadHistory()
    uni.showToast({ title: '锅仔收到啦，谢谢你', icon: 'success' })
  }
  catch (error: any) {
    uni.showToast({ title: error.message || '提交失败', icon: 'none' })
  }
  finally {
    sending.value = false
  }
}

onShow(loadHistory)
</script>

<template>
  <view class="feedback-page">
    <wd-navbar
      title="反馈建议"
      left-arrow
      safe-area-inset-top
      custom-style="background-color: transparent !important;"
      @click-left="navBack"
    />

    <view class="feedback-hero">
      <view class="hero-copy">
        <text class="feedback-kicker">
          锅仔倾听站
        </text>
        <text class="feedback-title">
          你的每一句话，锅仔都会认真听。
        </text>
        <view class="hero-note">
          <view class="note-dot" />
          <text>建议会进入产品改进清单</text>
        </view>
      </view>
      <view class="guozai-stage">
        <view class="stage-glow" />
        <image src="/static/guozai/action_08_peek.png" mode="aspectFit" />
      </view>
    </view>

    <view class="feedback-card">
      <view class="form-section">
        <view class="section-heading">
          <text class="step-number">
            1
          </text>
          <view>
            <text class="section-title">
              这次想说什么？
            </text>
            <text class="section-desc">
              选择最接近的一类就好
            </text>
          </view>
        </view>
        <view class="chips" role="radiogroup" aria-label="反馈类型">
          <view
            v-for="item in categories"
            :key="item"
            class="chip" :class="[{ active: category === item }]"
            role="radio"
            :aria-checked="category === item"
            @click="category = item"
          >
            <view class="chip-mark" />
            <text>{{ item }}</text>
          </view>
        </view>
      </view>

      <view class="form-divider" />

      <view class="form-section">
        <view class="section-heading">
          <text class="step-number">
            2
          </text>
          <view>
            <text class="section-title">
              具体说说吧
            </text>
            <text class="section-desc">
              问题出现在什么地方？你希望怎样改？
            </text>
          </view>
        </view>
        <view class="textarea-shell">
          <textarea
            v-model="content"
            :maxlength="1000"
            auto-height
            placeholder="例如：希望时光机可以按月份筛选……"
            placeholder-class="input-placeholder"
          />
          <text class="count">
            {{ content.length }}/1000
          </text>
        </view>
      </view>

      <view class="form-divider" />

      <view class="form-section">
        <view class="section-heading">
          <text class="step-number">
            3
          </text>
          <view>
            <text class="section-title">
              方便联系你吗？
            </text>
            <text class="section-desc">
              选填，仅用于反馈沟通
            </text>
          </view>
        </view>
        <input
          v-model="contact"
          :maxlength="100"
          placeholder="微信号或邮箱"
          placeholder-class="input-placeholder"
        >
        <text class="privacy-hint">
          请勿填写身份证、银行卡等敏感信息
        </text>
      </view>

      <view class="submit-note">
        <view class="submit-note-mark">
          <view />
          <view />
          <view />
        </view>
        <text>提交后可在下方查看反馈进度</text>
      </view>

      <button
        class="submit-button"
        :class="{ disabled: !canSubmit }"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{ sending ? '正在送给锅仔…' : '把建议交给锅仔' }}
      </button>
    </view>

    <view class="feedback-history">
      <view class="history-heading">
        <view>
          <text class="history-title">
            我的反馈
          </text>
          <text class="history-desc">
            每一个声音都有回音
          </text>
        </view>
        <text v-if="history.length" class="history-count">
          {{ history.length }} 条
        </text>
      </view>

      <view v-if="history.length" class="history-list">
        <view v-for="item in history" :key="item.id" class="history-item">
          <view class="history-meta">
            <text class="history-category">
              {{ item.category }}
            </text>
            <text class="history-date">
              {{ formatDate(item.createdAt) }}
            </text>
          </view>
          <text class="history-content">
            {{ item.content }}
          </text>
          <view class="history-progress">
            <view class="progress-line" />
            <text class="history-status" :class="[`status-${item.status.toLowerCase()}`]">
              {{ statusLabel(item.status) }}
            </text>
          </view>
        </view>
      </view>

      <view v-else class="history-empty">
        <image src="/static/guozai/action_07_empty.png" mode="aspectFit" />
        <view>
          <text class="empty-title">
            还没有反馈记录
          </text>
          <text class="empty-desc">
            第一条建议，锅仔在这里等你。
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.feedback-page {
  min-height: 100vh;
  padding: 0 28rpx calc(48rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background:
    radial-gradient(circle at 92% 6%, var(--mrc-accent-soft) 0, transparent 26%),
    var(--mrc-bg);
}

.feedback-hero {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 230rpx;
  margin: 12rpx 0 22rpx;
  padding: 36rpx 26rpx 32rpx 32rpx;
  overflow: hidden;
  box-sizing: border-box;
  border: 2rpx solid var(--mrc-border);
  border-radius: 36rpx;
  background: linear-gradient(135deg, var(--mrc-surface-peach), var(--mrc-surface));
  box-shadow: 0 18rpx 48rpx rgba(93, 59, 38, 0.08);
}

.feedback-hero::after {
  position: absolute;
  right: -48rpx;
  bottom: -78rpx;
  width: 240rpx;
  height: 240rpx;
  border: 2rpx solid var(--mrc-border);
  border-radius: 50%;
  content: '';
}

.hero-copy {
  position: relative;
  z-index: 2;
  flex: 1;
  min-width: 0;
}

.feedback-kicker {
  display: inline-flex;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
  font-size: 21rpx;
  font-weight: 800;
  letter-spacing: 1rpx;
}

.feedback-title {
  display: block;
  max-width: 390rpx;
  margin-top: 16rpx;
  color: var(--mrc-text-deep);
  font-size: 35rpx;
  font-weight: 800;
  line-height: 1.42;
}

.hero-note {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
  color: var(--mrc-text-sub);
  font-size: 22rpx;
}

.note-dot {
  width: 10rpx;
  height: 10rpx;
  margin-right: 10rpx;
  border-radius: 50%;
  background: var(--mrc-accent);
  box-shadow: 0 0 0 7rpx var(--mrc-accent-soft);
}

.guozai-stage {
  position: relative;
  z-index: 1;
  flex: 0 0 188rpx;
  height: 188rpx;
  margin-right: -8rpx;
}

.guozai-stage image {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
}

.stage-glow {
  position: absolute;
  inset: 24rpx 8rpx 4rpx 18rpx;
  border-radius: 50%;
  background: var(--mrc-accent-soft);
}

.feedback-card,
.feedback-history {
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 32rpx;
  background: var(--mrc-surface);
  box-shadow: 0 12rpx 38rpx rgba(93, 59, 38, 0.06);
}

.feedback-card {
  padding: 32rpx 28rpx 28rpx;
}

.form-section {
  width: 100%;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
  margin-bottom: 22rpx;
}

.step-number {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 42rpx;
  width: 42rpx;
  height: 42rpx;
  border-radius: 14rpx;
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
  font-size: 22rpx;
  font-weight: 900;
}

.section-title,
.section-desc {
  display: block;
}

.section-title {
  color: var(--mrc-text-deep);
  font-size: 28rpx;
  font-weight: 800;
  line-height: 1.4;
}

.section-desc {
  margin-top: 4rpx;
  color: var(--mrc-text-light);
  font-size: 21rpx;
  line-height: 1.45;
}

.form-divider {
  height: 2rpx;
  margin: 30rpx 0;
  background: var(--mrc-border-light);
}

.chips {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14rpx;
}

.chip {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  border: 2rpx solid transparent;
  border-radius: 22rpx;
  background: var(--mrc-bg);
  color: var(--mrc-text-sub);
  font-size: 25rpx;
  transition: transform 160ms ease, border-color 160ms ease, background-color 160ms ease;
}

.chip:active {
  transform: scale(0.98);
}

.chip.active {
  border-color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
  font-weight: 800;
}

.chip-mark {
  width: 14rpx;
  height: 14rpx;
  margin-right: 14rpx;
  box-sizing: border-box;
  border: 3rpx solid var(--mrc-border);
  border-radius: 50%;
  background: var(--mrc-surface);
}

.chip.active .chip-mark {
  border-color: var(--mrc-surface);
  background: var(--mrc-accent);
  box-shadow: 0 0 0 3rpx var(--mrc-accent);
}

.textarea-shell {
  padding: 22rpx 22rpx 16rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx;
  background: var(--mrc-bg);
  transition: border-color 160ms ease;
}

textarea {
  width: 100%;
  min-height: 210rpx;
  color: var(--mrc-text-deep);
  font-size: 26rpx;
  line-height: 1.65;
}

.count {
  display: block;
  margin-top: 8rpx;
  color: var(--mrc-text-light);
  font-size: 21rpx;
  text-align: right;
}

input {
  width: 100%;
  height: 88rpx;
  padding: 0 22rpx;
  box-sizing: border-box;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 22rpx;
  background: var(--mrc-bg);
  color: var(--mrc-text-deep);
  font-size: 26rpx;
}

:deep(.input-placeholder) {
  color: var(--mrc-text-light);
}

.privacy-hint {
  display: block;
  margin-top: 12rpx;
  color: var(--mrc-text-light);
  font-size: 20rpx;
  line-height: 1.5;
}

.submit-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  margin: 32rpx 0 18rpx;
  color: var(--mrc-text-sub);
  font-size: 22rpx;
}

.submit-note-mark {
  display: flex;
  gap: 4rpx;
  padding: 8rpx 10rpx;
  border-radius: 16rpx 16rpx 16rpx 4rpx;
  background: var(--mrc-accent-soft);
}

.submit-note-mark view {
  width: 5rpx;
  height: 5rpx;
  border-radius: 50%;
  background: var(--mrc-accent);
}

.submit-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100rpx;
  margin: 0;
  padding: 0 28rpx;
  border: 0;
  border-radius: 999rpx;
  background: var(--mrc-primary-grad);
  box-shadow: 0 14rpx 28rpx rgba(234, 116, 74, 0.22);
  color: #fff;
  font-size: 29rpx;
  font-weight: 800;
  line-height: 1;
  transition: transform 160ms ease, opacity 160ms ease;
}

.submit-button::after {
  border: 0;
}

.submit-button:active {
  transform: scale(0.985);
}

.submit-button.disabled {
  box-shadow: none;
  opacity: 0.45;
}

.feedback-history {
  margin-top: 24rpx;
  padding: 28rpx;
}

.history-heading,
.history-meta,
.history-progress,
.history-empty {
  display: flex;
  align-items: center;
}

.history-heading {
  justify-content: space-between;
  margin-bottom: 22rpx;
}

.history-title,
.history-desc {
  display: block;
}

.history-title {
  color: var(--mrc-text-deep);
  font-size: 29rpx;
  font-weight: 800;
}

.history-desc {
  margin-top: 5rpx;
  color: var(--mrc-text-light);
  font-size: 21rpx;
}

.history-count {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: var(--mrc-bg);
  color: var(--mrc-text-sub);
  font-size: 21rpx;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.history-item {
  padding: 22rpx;
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 22rpx;
  background: var(--mrc-bg);
}

.history-meta {
  justify-content: space-between;
  gap: 16rpx;
}

.history-category {
  color: var(--mrc-accent);
  font-size: 22rpx;
  font-weight: 800;
}

.history-date {
  color: var(--mrc-text-light);
  font-size: 20rpx;
}

.history-content {
  display: block;
  margin-top: 12rpx;
  color: var(--mrc-text-deep);
  font-size: 25rpx;
  line-height: 1.6;
}

.history-progress {
  margin-top: 18rpx;
}

.progress-line {
  flex: 1;
  height: 2rpx;
  margin-right: 14rpx;
  background: var(--mrc-border-light);
}

.history-status {
  flex-shrink: 0;
  padding: 7rpx 14rpx;
  border-radius: 999rpx;
  background: var(--mrc-accent-soft);
  color: var(--mrc-accent);
  font-size: 20rpx;
  font-weight: 700;
}

.status-resolved,
.status-closed {
  background: var(--mrc-bg);
  color: var(--mrc-text-sub);
}

.history-empty {
  min-height: 140rpx;
  padding: 10rpx 8rpx 4rpx;
}

.history-empty image {
  flex: 0 0 118rpx;
  width: 118rpx;
  height: 118rpx;
  margin-right: 18rpx;
}

.empty-title,
.empty-desc {
  display: block;
}

.empty-title {
  color: var(--mrc-text-deep);
  font-size: 25rpx;
  font-weight: 800;
}

.empty-desc {
  margin-top: 8rpx;
  color: var(--mrc-text-light);
  font-size: 21rpx;
  line-height: 1.5;
}

@media (prefers-reduced-motion: reduce) {
  .chip,
  .submit-button {
    transition: none;
  }
}
</style>
