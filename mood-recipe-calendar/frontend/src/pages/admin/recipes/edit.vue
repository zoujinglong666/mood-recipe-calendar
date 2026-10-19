<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { adminRecipeDetail, adminRecipeUpdate, adminRecipeDelete, type AdminRecipe } from '@/api/admin'
import { resolveAssetUrl, uploadFile } from '@/api/request'
import { navBack } from '@/composables/useNavBar'
import { toastError, toastSuccess } from '@/utils/toast'
import { chooseImageFile } from '@/utils/chooseImage'

definePage({
  name: 'admin-recipe-edit',
  layout: 'default',
  style: { navigationStyle: 'custom', navigationBarTitleText: '编辑菜谱' },
})

const router = useRouter()
const route = useRoute()
// 同 record/edit：@wot-ui/router 的 route.query 不可靠（name 跳转会丢弃 query 字段），
// 改用 uni-app 原生 onLoad 的 options 取 id。
const id = ref(0)
onLoad((options: any) => {
  const raw = options?.id ?? route.query?.id ?? (route as any).params?.id
  id.value = Number(raw)
})

const recipe = ref<AdminRecipe | null>(null)
const saving = ref(false)
const uploading = ref(false)

const form = ref({
  name: '',
  description: '',
  image: '',
  ingredients: '',
  steps: '',
  cookingTime: 0,
  difficulty: '',
  moodTags: '',
  taste: '',
  mealType: '',
  tags: '',
  seasonings: '',
  season: '',
})

onMounted(async () => {
  if (!id.value || Number.isNaN(id.value)) {
    toastError('缺少菜谱 ID，请从列表进入')
    setTimeout(() => navBack(), 1200)
    return
  }
  try {
    recipe.value = await adminRecipeDetail(id.value)
    Object.assign(form.value, {
      name: recipe.value.name || '',
      description: recipe.value.description || '',
      image: recipe.value.image || '',
      ingredients: recipe.value.ingredients || '',
      steps: recipe.value.steps || '',
      cookingTime: recipe.value.cookingTime || 0,
      difficulty: recipe.value.difficulty || '',
      moodTags: recipe.value.moodTags || '',
      taste: recipe.value.taste || '',
      mealType: recipe.value.mealType || '',
      tags: recipe.value.tags || '',
      seasonings: recipe.value.seasonings || '',
      season: recipe.value.season || '',
    })
  } catch (e: any) {
    toastError(e.message || '加载失败')
  }
})

async function onUploadImage() {
  chooseImageFile({
    onSelected: async (tempPath: string) => {
      uploading.value = true
      try {
        const uploadRes = await uploadFile(tempPath, 'image')
        form.value.image = uploadRes.url
        toastSuccess('图片上传成功')
      } catch (e: any) {
        toastError(e.message || '上传失败')
      } finally {
        uploading.value = false
      }
    },
    onFail: () => toastError('选择图片失败'),
  })
}

async function onSave() {
  if (!form.value.name.trim()) {
    toastError('菜名不能为空')
    return
  }
  saving.value = true
  try {
    await adminRecipeUpdate(id.value, { ...form.value })
    toastSuccess('保存成功')
    setTimeout(() => navBack(), 800)
  } catch (e: any) {
    toastError(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function onDelete() {
  uni.showModal({
    title: '确认删除',
    content: `确定删除「${form.value.name}」吗？此操作不可恢复。`,
    confirmColor: '#D94841',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await adminRecipeDelete(id.value)
        toastSuccess('已删除')
        setTimeout(() => navBack(), 800)
      } catch (e: any) {
        toastError(e.message || '删除失败')
      }
    },
  })
}
</script>

<template>
  <view class="edit-page" v-if="recipe">
    <wd-navbar title="编辑菜谱" left-arrow safe-area-inset-top custom-style="background-color: transparent !important;" @click-left="navBack" />

    <view class="edit-content">
    <!-- 当前图片 -->
    <view class="image-section">
      <image
        v-if="form.image"
        :src="resolveAssetUrl(form.image)"
        class="preview-img"
        mode="aspectFill"
      />
      <view v-else class="preview-img empty">
        <text>暂无图片</text>
      </view>
      <button class="upload-btn" :disabled="uploading" @click="onUploadImage">
        {{ uploading ? '上传中...' : '更换图片' }}
      </button>
      <text v-if="form.image && form.image.startsWith('/static/')" class="warn-tip">
        ⚠ 当前是占位假图，请上传真实菜品图
      </text>
    </view>

    <!-- 表单 -->
    <view class="form-card">
      <view class="form-row">
        <text class="label">菜名</text>
        <input v-model="form.name" class="input" placeholder="菜名" />
      </view>
      <view class="form-row">
        <text class="label">描述</text>
        <textarea v-model="form.description" class="textarea" placeholder="一句话描述" />
      </view>
      <view class="form-row">
        <text class="label">食材</text>
        <textarea v-model="form.ingredients" class="textarea" placeholder="逗号分隔" />
      </view>
      <view class="form-row">
        <text class="label">步骤</text>
        <textarea v-model="form.steps" class="textarea tall" placeholder="每步换行" />
      </view>
      <view class="form-row">
        <text class="label">时间(分)</text>
        <input v-model.number="form.cookingTime" class="input" type="number" />
      </view>
      <view class="form-row">
        <text class="label">难度</text>
        <input v-model="form.difficulty" class="input" placeholder="简单/中等/困难" />
      </view>
      <view class="form-row">
        <text class="label">口味</text>
        <input v-model="form.taste" class="input" placeholder="清淡,辣,甜" />
      </view>
      <view class="form-row">
        <text class="label">餐别</text>
        <input v-model="form.mealType" class="input" placeholder="早餐/午餐/晚餐" />
      </view>
      <view class="form-row">
        <text class="label">心情标签</text>
        <input v-model="form.moodTags" class="input" placeholder="平静,开心" />
      </view>
      <view class="form-row">
        <text class="label">标签</text>
        <input v-model="form.tags" class="input" placeholder="快手菜,汤品" />
      </view>
      <view class="form-row">
        <text class="label">调料</text>
        <input v-model="form.seasonings" class="input" placeholder="盐,酱油" />
      </view>
    </view>

    <!-- 操作按钮 -->
    <view class="action-bar">
      <button class="save-btn" :disabled="saving" @click="onSave">
        {{ saving ? '保存中...' : '保存' }}
      </button>
      <button class="delete-btn" @click="onDelete">删除此菜谱</button>
    </view>
    </view>
  </view>
</template>

<style scoped>
/* 与全局主题（theme.scss --mrc-* 令牌）统一 */
.edit-page {
  min-height: 100vh;
  min-height: 100dvh;
  box-sizing: border-box;
  font-family: var(--mrc-font-family);
  color: var(--mrc-text);
  background: var(--mrc-bg);
}
.edit-content {
  width: calc(100% - 48rpx);
  max-width: 820rpx;
  margin: 0 auto;
  padding: 20rpx 0 calc(52rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.image-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 28rpx;
}
.preview-img {
  width: 400rpx;
  height: 300rpx;
  border-radius: 28rpx;
  background: var(--mrc-surface-2);
  border: 2rpx solid var(--mrc-border-light);
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
  box-sizing: border-box;
}
.preview-img.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mrc-text-light);
}
.upload-btn {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-primary);
  color: var(--mrc-accent);
  font-size: 26rpx;
  font-weight: 700;
  border-radius: 999rpx;
  padding: 0 44rpx;
  line-height: 72rpx;
  margin: 0;
  box-shadow: var(--mrc-shadow-soft);
  transition: transform var(--mrc-motion-fast, 180ms ease-out);
}
.upload-btn::after {
  border: none;
}
.upload-btn:active {
  transform: scale(0.97);
}
.warn-tip {
  color: var(--mrc-accent);
  background: var(--mrc-accent-soft);
  font-size: 22rpx;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
}

.form-card {
  background: var(--mrc-surface);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 28rpx;
  padding: 28rpx 26rpx;
  box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss);
  box-sizing: border-box;
}
.form-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 22rpx;
  gap: 16rpx;
}
.form-row:last-child {
  margin-bottom: 0;
}
.label {
  width: 140rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--mrc-text-deep);
  line-height: 76rpx;
  flex-shrink: 0;
}
.input,
.textarea {
  flex: 1;
  min-width: 0;
  background: var(--mrc-surface-2);
  border: 2rpx solid transparent;
  border-radius: 18rpx;
  padding: 16rpx 22rpx;
  font-size: 26rpx;
  color: var(--mrc-text-deep);
  transition: border-color 180ms ease, background-color 180ms ease;
}
.textarea {
  min-height: 110rpx;
  line-height: 1.6;
}
.textarea.tall {
  min-height: 220rpx;
}
.input:focus,
.textarea:focus {
  border-color: var(--mrc-primary);
  background: var(--mrc-surface);
}

.action-bar {
  display: flex;
  gap: 20rpx;
  margin-top: 36rpx;
}
.save-btn,
.delete-btn {
  flex: 1;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 30rpx;
  font-weight: 700;
  border-radius: 48rpx;
  margin: 0;
  padding: 0;
  transition: transform var(--mrc-motion-fast, 180ms ease-out), box-shadow var(--mrc-motion-fast, 180ms ease-out);
}
.save-btn::after,
.delete-btn::after {
  border: none;
}
.save-btn {
  background: var(--mrc-primary-grad);
  color: #fff;
  box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss);
}
.delete-btn {
  background: var(--mrc-surface);
  color: var(--mrc-color-danger);
  border: 2rpx solid var(--mrc-color-danger-soft);
}
.save-btn:active,
.delete-btn:active {
  transform: scale(0.97);
}
.save-btn[disabled],
.delete-btn[disabled] {
  opacity: 0.5;
}

@media (prefers-reduced-motion: reduce) {
  .upload-btn,
  .save-btn,
  .delete-btn {
    transition: none;
  }
  .upload-btn:active,
  .save-btn:active,
  .delete-btn:active {
    transform: none;
  }
}
</style>
