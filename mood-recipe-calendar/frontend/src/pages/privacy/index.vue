<script setup lang="ts">
import Icon from '@/components/common/Icon.vue'
import { navBack } from '@/composables/useNavBar'

definePage({ name: 'privacy', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '隐私政策' } })
const router = useRouter()
const updatedAt = '2026 年 9 月 7 日'
</script>

<template>
  <view class="privacy-page">
    <wd-navbar title="隐私政策" left-arrow safe-area-inset-top @click-left="navBack" custom-style="background-color: transparent !important;" />
    <view class="privacy-hero">
      <view><text class="privacy-kicker">GUOZAI CARES</text><text class="privacy-title">你的每一餐，<br />只属于你。</text><text class="privacy-date">最近更新：{{ updatedAt }}</text></view>
      <image src="/static/guozai/action_06_glasses.png" mode="aspectFit" />
    </view>
    <view class="privacy-card"><Icon name="heart" :size="40" color="var(--mrc-accent)" /><view><text class="privacy-card__title">我们收集什么</text><text>仅保存你主动提交的昵称、头像、菜品记录、图片、心情、反馈、数字权益订单，以及首页打开的粗粒度时段事件。AI 推荐仅保存菜名的匿名指纹和“喜欢、不想吃、做过”状态，不保存模型推理或完整菜谱正文。</text></view></view>
    <view class="privacy-card"><Icon name="book" :size="40" color="var(--mrc-accent)" /><view><text class="privacy-card__title">如何使用</text><text>用于展示日历、时光机、画册、生成 AI 菜谱与锅仔个性寄语。发送给 AI 服务的仅是聚合习惯摘要，不含 openid、昵称、图片和日记正文；运营事件只记录类型与时间。</text></view></view>
    <view class="privacy-card"><Icon name="user" :size="40" color="var(--mrc-accent)" /><view><text class="privacy-card__title">你的选择</text><text>你可在“反馈建议”提交数据导出、删除或隐私相关请求。请求处理完成前，我们会先核验账号归属。</text></view></view>
    <view class="privacy-card"><Icon name="heart" :size="40" color="var(--mrc-accent)" /><view><text class="privacy-card__title">不会做的事</text><text>不会出售你的个人信息；不会把菜谱正文用于广告定向；不会把支付密钥、session_key 下发到客户端。</text></view></view>
    <view class="privacy-action" @click="router.push({ name: 'feedback' })"><text>提交隐私或数据请求</text><text>›</text></view>
  </view>
</template>

<style lang="scss" scoped>
.privacy-page{min-height:100vh;padding:0 32rpx calc(48rpx + env(safe-area-inset-bottom));box-sizing:border-box;background:var(--mrc-bg)}.privacy-hero{display:flex;align-items:center;justify-content:space-between;min-height:280rpx;margin:12rpx 0 24rpx;padding:32rpx;border:2rpx solid var(--mrc-border);border-radius:36rpx;background:linear-gradient(135deg,var(--mrc-surface-peach),var(--mrc-surface-sun));box-sizing:border-box}.privacy-hero image{width:210rpx;height:210rpx;margin-right:-34rpx}.privacy-kicker,.privacy-date{display:block;font-size:20rpx;color:var(--mrc-accent);font-weight:800;letter-spacing:2rpx}.privacy-title{display:block;margin-top:12rpx;font-size:40rpx;line-height:1.35;font-weight:800;color:var(--mrc-text-deep)}.privacy-date{margin-top:16rpx;color:var(--mrc-text-sub);letter-spacing:0;font-weight:500}.privacy-card{display:flex;gap:18rpx;margin-top:16rpx;padding:26rpx;border:2rpx solid var(--mrc-border-light);border-radius:26rpx;background:var(--mrc-surface);box-shadow:var(--mrc-shadow-soft)}.privacy-card view{flex:1}.privacy-card text{display:block;color:var(--mrc-text-sub);font-size:25rpx;line-height:1.65}.privacy-card__title{margin-bottom:8rpx;color:var(--mrc-text-deep)!important;font-size:29rpx!important;font-weight:800}.privacy-action{display:flex;align-items:center;justify-content:space-between;min-height:96rpx;margin-top:24rpx;padding:0 30rpx;border-radius:48rpx;background:var(--mrc-primary-grad);color:#fff;font-size:29rpx;font-weight:800;box-shadow:var(--mrc-shadow-soft)}
</style>
