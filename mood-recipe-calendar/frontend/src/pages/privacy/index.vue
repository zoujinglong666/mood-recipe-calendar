<script setup lang="ts">
import Icon from '@/components/common/Icon.vue'
import { navBack } from '@/composables/useNavBar'
import { STATIC_BASE_URL } from '@/utils/assets'

definePage({ name: 'privacy', layout: 'default', style: { navigationStyle: 'custom', navigationBarTitleText: '隐私政策' } })
const router = useRouter()
const updatedAt = '2026 年 9 月 17 日'
</script>

<template>
  <view class="privacy-page">
    <wd-navbar title="隐私政策" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />
    <view class="privacy-hero">
      <view>
        <text class="privacy-kicker">
          GUOZAI CARES
        </text><text class="privacy-title">
          你的每一餐，<br>只属于你。
        </text><text class="privacy-date">
          最近更新：{{ updatedAt }}
        </text>
      </view>
      <image :src="`${STATIC_BASE_URL}/static/guozai/action_06_glasses.png`" mode="aspectFit" />
    </view>
    <view class="privacy-card">
      <Icon name="heart" :size="40" color="#EF5A3C" /><view>
        <text class="privacy-card__title">
          我们收集什么
        </text><text>仅保存你主动提交的昵称、头像、菜品记录与图片、心情、忌口和过敏原、家庭用餐条件、反馈、AI 对话中选择记住的偏好、数字权益订单，以及用于排障的最小事件类型与时间。我们不保存模型的隐藏推理过程。</text>
      </view>
    </view>
    <view class="privacy-card">
      <Icon name="book" :size="40" color="#EF5A3C" /><view>
        <text class="privacy-card__title">
          如何使用与第三方
        </text><text>数据用于日历、时光机、画册、AI 菜谱、个性寄语和权益交付。微信提供登录、隐私授权、内容安全与虚拟支付；腾讯云 COS 保存你主动上传或生成的图片；Agnes AI 处理生成菜谱、对话和图片所需的最小上下文。发送给 AI 的请求不包含 openid、支付凭据或 session_key。</text>
      </view>
    </view>
    <view class="privacy-card">
      <Icon name="user" :size="40" color="#EF5A3C" /><view>
        <text class="privacy-card__title">
          保存期限与儿童信息
        </text><text>账号资料和记录保存至你主动删除或注销账号；图片删除失败会进入限次重试队列；依法需要留存的订单会去标识化后按法定期限保存。我们只记录“有老人/有小孩”等用餐条件，不主动收集儿童姓名、头像、生日或疾病信息；请勿在自由文本中提交此类信息。</text>
      </view>
    </view>
    <view class="privacy-card">
      <Icon name="user" :size="40" color="#EF5A3C" /><view>
        <text class="privacy-card__title">
          你的选择
        </text><text>你可以在“设置”中查看、单条删除或清空锅仔记忆，关闭个性化推荐，或永久注销账号。你也可以通过“反馈建议”申请数据导出、更正或咨询；处理前我们会核验账号归属。</text>
      </view>
    </view>
    <view class="privacy-card">
      <Icon name="heart" :size="40" color="#EF5A3C" /><view>
        <text class="privacy-card__title">
          AI 与安全边界
        </text><text>AI 生成或辅助生成的文字和图片会进行标识。菜谱仅供日常烹饪参考，不能替代医疗诊断和治疗建议；严重过敏、疾病、孕期或婴幼儿饮食请咨询专业人员。我们不会出售个人信息，也不会把支付密钥或 session_key 下发到客户端。</text>
      </view>
    </view>
    <view class="privacy-action" @click="router.push({ name: 'feedback' })">
      <text>提交隐私或数据请求</text><text>›</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.privacy-page{min-height:100vh;padding:0 32rpx calc(48rpx + env(safe-area-inset-bottom));box-sizing:border-box;background:var(--mrc-bg)}.privacy-hero{display:flex;align-items:center;justify-content:space-between;min-height:280rpx;margin:12rpx 0 24rpx;padding:32rpx;border:2rpx solid var(--mrc-border);border-radius:36rpx;background:linear-gradient(135deg,var(--mrc-surface-peach),var(--mrc-surface-sun));box-sizing:border-box}.privacy-hero image{width:210rpx;height:210rpx;margin-right:-34rpx}.privacy-kicker,.privacy-date{display:block;font-size:20rpx;color:var(--mrc-accent);font-weight:800;letter-spacing:2rpx}.privacy-title{display:block;margin-top:12rpx;font-size:40rpx;line-height:1.35;font-weight:800;color:var(--mrc-text-deep)}.privacy-date{margin-top:16rpx;color:var(--mrc-text-sub);letter-spacing:0;font-weight:500}.privacy-card{display:flex;gap:18rpx;margin-top:16rpx;padding:26rpx;border:2rpx solid var(--mrc-border-light);border-radius:26rpx;background:var(--mrc-surface);box-shadow:var(--mrc-shadow-soft)}.privacy-card view{flex:1}.privacy-card text{display:block;color:var(--mrc-text-sub);font-size:25rpx;line-height:1.65}.privacy-card__title{margin-bottom:8rpx;color:var(--mrc-text-deep)!important;font-size:29rpx!important;font-weight:800}.privacy-action{display:flex;align-items:center;justify-content:space-between;min-height:96rpx;margin-top:24rpx;padding:0 30rpx;border-radius:48rpx;background:var(--mrc-primary-grad);color:#fff;font-size:29rpx;font-weight:800;box-shadow:var(--mrc-shadow-soft)}
</style>
