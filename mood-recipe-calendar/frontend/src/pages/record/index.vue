<script setup lang="ts">
import { ref, computed } from 'vue'
import AppNav from '../../components/common/AppNav.vue'
import Icon from '../../components/common/Icon.vue'
import SuccessModal from '../../components/guozai/SuccessModal.vue'
import { ensureLogin } from '../../utils/login'
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

const route = useRoute()
const router = useRouter()

const MOODS = [
  { key: '开心', img: '/static/guozai/mood_01_happy.png' },
  { key: '平静', img: '/static/guozai/mood_02_calm.png' },
  { key: '疲惫', img: '/static/guozai/mood_03_tired.png' },
  { key: '焦虑', img: '/static/guozai/mood_04_anxious.png' },
  { key: '难过', img: '/static/guozai/mood_05_sad.png' },
  { key: '嘴馋', img: '/static/guozai/mood_06_hungry.png' },
  { key: '低落', img: '/static/guozai/mood_07_low.png' },
  { key: '想家', img: '/static/guozai/mood_08_homesick.png' },
]

const dishName = ref((route.query.dish as string) || '')
const selectedMood = ref((route.query.mood as string) || '')
const note = ref('')
const cookingTime = ref('30分钟')
const dishImage = ref('')
const imageUrl = ref('')
const showSuccess = ref(false)
const submitting = ref(false)
const uploading = ref(false)

function pickMood(key: string) {
  selectedMood.value = key
  try { uni.vibrateShort({ type: 'light' }) } catch {}
}

async function chooseImage() {
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
        uni.showToast({ title: '图片上传失败', icon: 'none' })
        dishImage.value = ''
      } finally {
        uploading.value = false
      }
    },
  })
}

async function publish() {
  if (!dishImage.value) {
    uni.showToast({ title: '请先上传菜品照片', icon: 'none' })
    return
  }
  if (uploading.value) {
    uni.showToast({ title: '图片上传中，请稍候', icon: 'none' })
    return
  }
  if (!dishName.value.trim()) {
    uni.showToast({ title: '请输入菜名', icon: 'none' })
    return
  }
  if (!selectedMood.value) {
    uni.showToast({ title: '请选择今天的心情', icon: 'none' })
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
      cookingTime: parseInt(cookingTime.value) || 30,
    })
    showSuccess.value = true
  } catch (e: any) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function onSuccessConfirm() {
  showSuccess.value = false
  router.push({ name: 'calendar' })
}
</script>

<template>
  <view class="record-page">
    <AppNav title="记录今日伙食" />

    <!-- 拍照区 -->
    <view class="record-photo" @click="chooseImage">
      <image v-if="dishImage" class="record-photo__img" :src="dishImage" mode="aspectFill" />
      <template v-else>
        <image class="record-photo__guozai guozai-breathe" src="/static/guozai/action_03_camera.png" mode="aspectFit" />
        <view class="record-photo__camera">
          <Icon name="camera" :size="80" color="#C9A87C" />
        </view>
        <text class="record-photo__tip">点击拍照 / 从相册选择</text>
      </template>
    </view>

    <!-- 菜名输入 -->
    <view class="record-input">
      <text class="record-input__label">菜名</text>
      <input
        class="record-input__field"
        v-model="dishName"
        placeholder="请输入菜名"
        placeholder-class="record-input__placeholder"
      />
    </view>

    <!-- 心情选择 -->
    <view class="record-mood">
      <text class="record-mood__title">今天的心情</text>
      <view class="record-mood__grid">
        <view
          v-for="m in MOODS"
          :key="m.key"
          class="record-mood__item"
          :class="{ 'record-mood__item--active': selectedMood === m.key }"
          @click="pickMood(m.key)"
        >
          <image class="record-mood__icon" :src="m.img" mode="aspectFit" />
          <text class="record-mood__label">{{ m.key }}</text>
        </view>
      </view>
    </view>

    <!-- 心情日记 -->
    <view class="record-textarea">
      <textarea
        class="record-textarea__field"
        v-model="note"
        placeholder="一句话记录今天的心情（限50字）"
        placeholder-class="record-textarea__placeholder"
        maxlength="50"
        :auto-height="true"
      />
    </view>

    <!-- 烹饪时间 -->
    <view class="record-time">
      <text class="record-time__label">烹饪时间（可选）</text>
      <view class="record-time__tag">{{ cookingTime }}</view>
    </view>

    <!-- 发布按钮 -->
    <view class="record-submit">
      <view class="record-submit__btn" @click="publish">发布</view>
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
  background: #FDFAEC;
  padding: 0 32rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* 拍照区 */
.record-photo {
  position: relative;
  width: 100%;
  height: 400rpx;
  background: var(--mrc-bg-soft);
  border-radius: 36rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
  overflow: hidden;
}
.record-photo__img {
  width: 100%;
  height: 100%;
}
.record-photo__guozai {
  position: absolute;
  top: -30rpx;
  right: 20rpx;
  width: 180rpx;
  height: 180rpx;
  z-index: 2;
}
.record-photo__camera {
  margin-bottom: 20rpx;
}
.record-photo__tip {
  font-size: 30rpx;
  color: #A08672;
}

/* 输入框 */
.record-input {
  display: flex;
  align-items: center;
  background: var(--mrc-bg-soft);
  border-radius: 48rpx;
  padding: 0 32rpx;
  height: 96rpx;
  margin-bottom: 32rpx;
}
.record-input__label {
  font-size: 32rpx;
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

/* 心情选择 */
.record-mood {
  margin-bottom: 32rpx;
}
.record-mood__title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--mrc-text-deep);
  margin-bottom: 24rpx;
}
.record-mood__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}
.record-mood__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  width: 130rpx;
  height: 130rpx;
  border-radius: 50%;
  background: var(--mrc-bg-soft);
  margin: 0 auto;
  transition: all 0.2s ease;
}
.record-mood__item--active {
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  transform: scale(1.08);
  box-shadow: 0 6rpx 16rpx rgba(253, 145, 132, 0.35);
}
.record-mood__icon {
  width: 48rpx;
  height: 48rpx;
}
.record-mood__label {
  font-size: 22rpx;
  color: var(--mrc-text-deep);
  font-weight: 500;
}
.record-mood__item--active .record-mood__label {
  color: #fff;
  font-weight: 700;
}

/* 心情日记 */
.record-textarea {
  background: var(--mrc-bg-soft);
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 28rpx;
}
.record-textarea__field {
  width: 100%;
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  line-height: 1.6;
  min-height: 120rpx;
}
.record-textarea__placeholder {
  color: var(--mrc-text-light);
}

/* 烹饪时间 */
.record-time {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 40rpx;
}
.record-time__label {
  font-size: 30rpx;
  color: var(--mrc-text-deep);
  font-weight: 600;
}
.record-time__tag {
  font-size: 28rpx;
  color: var(--mrc-text-deep);
  background: var(--mrc-bg-soft);
  padding: 12rpx 32rpx;
  border-radius: 32rpx;
  border: 2rpx solid var(--mrc-border-light);
}

/* 发布按钮 */
.record-submit__btn {
  width: 100%;
  height: 100rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--mrc-primary), var(--mrc-primary-deep));
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
  border-radius: 50rpx;
  box-shadow: 0 10rpx 24rpx rgba(253, 145, 132, 0.35);
  letter-spacing: 4rpx;
}
.record-submit__btn:active {
  transform: scale(0.97);
}
</style>
