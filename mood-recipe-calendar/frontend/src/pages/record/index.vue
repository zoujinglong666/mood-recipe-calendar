<script setup lang="ts">
import { navBack } from '@/composables/useNavBar'
import { ref, computed } from 'vue'
import Icon from '../../components/common/Icon.vue'
import SuccessModal from '../../components/guozai/SuccessModal.vue'
import MoodPicker from '../../components/guozai/MoodPicker.vue'
import { ensureLogin } from '../../utils/login'
import { toast, toastError } from '../../utils/toast'
import { saveRecord } from '../../api/records'
import { uploadFile } from '../../api/request'

definePage({
  name: 'record',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '记录今日伙食',
  },
})

const router = useRouter()

const dishName = ref('')
const selectedMood = ref('')
const note = ref('')
const cookingTime = ref('30分钟')
const dishImage = ref('')
const imageUrl = ref('')
const recipeId = ref<string | undefined>()
const exposureId = ref<string | undefined>()
const showSuccess = ref(false)
const submitting = ref(false)
const uploading = ref(false)

// tabbar 页通过 switchTab 进入，无法带 query；从推荐页跳转时由 storage 暂存菜名与心情
onShow(() => {
  try {
    const d = uni.getStorageSync('mrc_record_draft')
    if (d) {
      if (d.dish) dishName.value = d.dish
      if (d.mood) selectedMood.value = d.mood
      if (d.recipeId !== undefined && d.recipeId !== null) recipeId.value = String(d.recipeId)
      if (d.exposureId) exposureId.value = String(d.exposureId)
      uni.removeStorageSync('mrc_record_draft')
    }
  } catch (e) { /* ignore */ }
})

async function chooseImage() {
  if (uploading.value) return
  uni.chooseImage({
    count: 1,
    success: async (res) => {
      const tempPath = res.tempFilePaths[0]
      dishImage.value = tempPath
      uploading.value = true
      try {
        const result = await uploadFile(tempPath)
        imageUrl.value = result.url
      } catch (e: any) {
        toast('图片上传失败')
        dishImage.value = ''
      } finally {
        uploading.value = false
      }
    },
  })
}

async function publish() {
  if (submitting.value) return
  if (!dishImage.value) {
    toast('请先上传菜品照片')
    return
  }
  if (uploading.value) {
    toast('图片上传中，请稍候')
    return
  }
  if (!dishName.value.trim()) {
    toast('请输入菜名')
    return
  }
  if (!selectedMood.value) {
    toast('请选择今天的心情')
    return
  }

  submitting.value = true
  try {
    const openid = await ensureLogin()
    await saveRecord({
      openid,
      imageUrl: imageUrl.value || dishImage.value,
      dishName: dishName.value.trim(),
      moodTag: selectedMood.value,
      note: note.value,
      recipeId: recipeId.value,
      exposureId: exposureId.value,
      cookingTime: parseInt(cookingTime.value) || 30,
    })
    uni.removeStorageSync('mrc_companion_message')
    showSuccess.value = true
  } catch (e: any) {
    toastError(e, '保存失败')
  } finally {
    submitting.value = false
  }
}

function onSuccessConfirm() {
  showSuccess.value = false
  router.push({ name: 'calendar' })
}

const COOKING_TIME_OPTIONS = ['10分钟', '20分钟', '30分钟', '45分钟', '60分钟', '1小时以上']
function chooseCookingTime() {
  uni.showActionSheet({
    itemList: COOKING_TIME_OPTIONS,
    success: (res) => {
      cookingTime.value = COOKING_TIME_OPTIONS[res.tapIndex]
    },
  })
}
</script>

<template>
  <view class="record-page mrc-hero">
    <wd-navbar title="记录今日伙食" safe-area-inset-top custom-style="background-color: transparent !important;" />

    <view class="record-intro">
      <view class="record-intro__copy">
        <text class="record-intro__eyebrow">今天也有好好吃饭</text>
        <text class="record-intro__title">把这一餐，留给以后的你</text>
        <text class="record-intro__sub">锅仔会记住味道，也记住你今天的心情</text>
      </view>
      <image class="record-intro__guozai" src="/static/guozai/action_03_camera.png" mode="aspectFit" />
    </view>

    <!-- 拍照区 -->
    <view class="record-photo" role="button" aria-label="添加或更换菜品照片" @click="chooseImage">
      <template v-if="dishImage">
        <image class="record-photo__img" :src="dishImage" mode="aspectFill" />
        <view class="record-photo__change">
          <Icon name="camera" :size="30" color="#FFFFFF" />
          <text>{{ uploading ? '上传中…' : '更换照片' }}</text>
        </view>
        <view v-if="uploading" class="record-photo__uploading"><text>锅仔正在收好照片…</text></view>
      </template>
      <template v-else>
        <view class="record-photo__camera">
          <Icon name="camera" :size="56" color="#EF5A3C" />
        </view>
        <text class="record-photo__title">先拍下今天这道菜</text>
        <text class="record-photo__tip">拍照或从相册选择 · 必填</text>
      </template>
    </view>

    <view class="record-form-card">
      <view class="record-section-title">
        <view class="record-section-title__num">2</view>
        <view><text class="record-section-title__main">这一餐吃了什么？</text><text class="record-section-title__sub">菜名必填，时间可以慢慢选</text></view>
      </view>
      <view class="record-input">
        <text class="record-input__label">菜名</text>
        <input
          v-model="dishName"
          class="record-input__field"
          placeholder="比如：番茄炒蛋"
          placeholder-class="record-input__placeholder"
        />
      </view>
      <view class="record-time" role="button" aria-label="选择烹饪时间" @click="chooseCookingTime">
        <view class="record-time__label"><Icon name="clock" :size="34" color="#EF5A3C" /><text>烹饪时间</text></view>
        <view class="record-time__tag">{{ cookingTime }}<text class="record-time__arrow">›</text></view>
      </view>
    </view>

    <view class="record-mood-card">
      <MoodPicker v-model="selectedMood" :show-hero="false" title="吃完这顿，你是什么心情？" />
    </view>

    <view class="record-textarea">
      <view class="record-textarea__head">
        <view><text class="record-textarea__title">留一句话给今天</text><text class="record-textarea__sub">可选 · 锅仔不会催你写很多</text></view>
        <text class="record-textarea__count">{{ note.length }}/50</text>
      </view>
      <textarea
        v-model="note"
        class="record-textarea__field"
        placeholder="今天这顿饭，有什么想记住的？"
        placeholder-class="record-textarea__placeholder"
        :maxlength="50"
        :auto-height="true"
      />
    </view>

    <view class="record-submit">
      <view class="record-submit__btn" :class="{ 'record-submit__btn--disabled': submitting || uploading }" role="button" aria-label="保存今日伙食记录" @click="publish">
        <text>{{ submitting ? '正在保存…' : '收进我的时光机' }}</text>
        <text v-if="!submitting" class="record-submit__sub">以后翻到今天，还能想起这一餐</text>
      </view>
    </view>

    <!-- 成功弹窗 -->
    <SuccessModal
      :visible="showSuccess"
      title="记录成功！"
      subtitle="今天也好好吃饭了呢"
      @confirm="onSuccessConfirm"
    />
  </view>
</template>

<style lang="scss" scoped>
.record-page {
  min-height: 100vh;
  padding: 0 32rpx;
  padding-bottom: calc(56rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.record-intro { position: relative; display: flex; min-height: 176rpx; margin: 4rpx 0 24rpx; padding: 28rpx 28rpx 24rpx; overflow: hidden; box-sizing: border-box; border: 2rpx solid var(--mrc-border); border-radius: 36rpx; background: linear-gradient(145deg, var(--mrc-surface), var(--mrc-surface-peach)); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.record-intro__copy { position: relative; z-index: 1; max-width: 76%; }
.record-intro__eyebrow, .record-intro__title, .record-intro__sub { display: block; }
.record-intro__eyebrow { margin-bottom: 8rpx; color: var(--mrc-accent); font-size: 20rpx; font-weight: 800; letter-spacing: 2rpx; }
.record-intro__title { color: var(--mrc-text-strong); font-size: 34rpx; font-weight: var(--mrc-fw-heavy); line-height: 1.3; }
.record-intro__sub { margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 22rpx; line-height: 1.45; }
.record-intro__guozai { position: absolute; right: -4rpx; bottom: -12rpx; width: 142rpx; height: 142rpx; }

/* 拍照区 */
.record-photo {
  position: relative;
  width: 100%;
  height: 344rpx;
  background: radial-gradient(circle at 78% 18%, rgba(255, 197, 61, 0.22), transparent 28%), var(--mrc-surface-sun);
  border: 2rpx dashed var(--mrc-border-strong);
  border-radius: 36rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
  overflow: hidden;
}
.record-photo__img {
  width: 100%;
  height: 100%;
}
.record-photo:active { opacity: .88; }
.record-photo__camera {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 104rpx;
  height: 104rpx;
  background: var(--mrc-surface);
  border-radius: 32rpx;
  box-shadow: var(--mrc-shadow-sm);
  margin-bottom: 16rpx;
}
.record-photo__title { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.record-photo__tip {
  margin-top: 8rpx;
  font-size: 27rpx;
  color: var(--mrc-text-sub);
}
.record-photo__change { position: absolute; right: 20rpx; bottom: 20rpx; display: flex; align-items: center; gap: 8rpx; min-height: 72rpx; padding: 0 22rpx; border-radius: 36rpx; background: rgba(44, 24, 16, .72); color: #fff; font-size: 23rpx; font-weight: 700; }
.record-photo__uploading { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(44, 24, 16, .45); color: #fff; font-size: 26rpx; font-weight: 700; }

.record-form-card, .record-mood-card, .record-textarea { margin-bottom: 24rpx; padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.record-section-title { display: flex; align-items: center; gap: 16rpx; margin-bottom: 22rpx; }
.record-section-title__num { width: 48rpx; height: 48rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 24rpx; font-weight: var(--mrc-fw-heavy); }
.record-section-title__main, .record-section-title__sub { display: block; }
.record-section-title__main { color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; }
.record-section-title__sub { margin-top: 4rpx; color: var(--mrc-text-sub); font-size: 20rpx; }

.record-input {
  display: flex;
  align-items: center;
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx;
  padding: 0 24rpx;
  height: 92rpx;
  margin-bottom: 14rpx;
}
.record-input__label {
  font-size: 27rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
  margin-right: 24rpx;
  flex-shrink: 0;
}
.record-input__field {
  flex: 1;
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  text-align: right;
}
.record-input__placeholder {
  color: var(--mrc-text-light);
}

.record-textarea__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; margin-bottom: 20rpx; }
.record-textarea__title, .record-textarea__sub { display: block; }
.record-textarea__title { color: var(--mrc-text-strong); font-size: 29rpx; font-weight: 800; }
.record-textarea__sub { margin-top: 5rpx; color: var(--mrc-text-sub); font-size: 20rpx; }
.record-textarea__count { color: var(--mrc-text-light); font-size: 21rpx; }
.record-textarea__field {
  width: 100%;
  min-height: 130rpx;
  padding: 20rpx 22rpx;
  box-sizing: border-box;
  border-radius: 22rpx;
  background: var(--mrc-surface-2);
  font-size: 27rpx;
  color: var(--mrc-text-deep);
  line-height: 1.6;
}
.record-textarea__placeholder {
  color: var(--mrc-text-light);
}

/* 烹饪时间 */
.record-time {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  padding: 0 18rpx 0 22rpx;
  border-radius: 24rpx;
  background: var(--mrc-surface-sun);
}
.record-time__label {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 26rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.record-time__tag {
  font-size: 25rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-surface-sun);
  padding: 10rpx 12rpx 10rpx 22rpx;
  border-radius: 32rpx;
  border: 2rpx solid var(--mrc-border-light);
  display: flex;
  align-items: center;
}
.record-time__arrow {
  margin-left: 8rpx;
  font-size: 32rpx;
  color: var(--mrc-text-light);
}

/* 发布按钮 */
.record-submit { padding-top: 4rpx; }
.record-submit__btn {
  width: 100%;
  min-height: 112rpx;
  flex-direction: column;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--mrc-primary-grad);
  color: #fff;
  font-size: 32rpx;
  font-weight: 700;
  border-radius: 50rpx;
  box-shadow: 0 10rpx 24rpx rgba(253, 145, 132, 0.35);
  letter-spacing: 4rpx;
}
.record-submit__sub { margin-top: 5rpx; font-size: 20rpx; font-weight: 500; letter-spacing: 0; opacity: .84; }
.record-submit__btn--disabled { opacity: .58; box-shadow: none; }
.record-submit__btn:active {
  transform: scale(0.97);
}

@media (prefers-reduced-motion: reduce) {
  .record-submit__btn { transition: none; }
}
</style>
