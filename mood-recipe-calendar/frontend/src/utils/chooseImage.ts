/**
 * 选图工具：统一封装相册/拍照选择
 *
 * 用 uni.chooseMedia（微信推荐，取代 chooseImage，工具与真机均稳定），
 * 并对失败做分类处理：取消静默、相册权限被拒引导去设置、隐私协议未同意引导查看。
 */
import { toast } from './toast'

interface ChooseImageOptions {
  /** 选中图片后的回调（临时路径） */
  onSelected: (tempPath: string) => void
  /** 非取消类失败回调（可选，默认 toast 通用提示） */
  onFail?: () => void
}

/** 相册/相机权限被拒时引导用户去设置开启 */
function guideToSettings() {
  uni.showModal({
    title: '需要相册权限',
    content: '开启相册/相机权限后，才能拍照或从相册选择照片记录美食。',
    confirmText: '去设置',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm)
        uni.openSetting()
    },
  })
}

/** 隐私协议未同意时引导查看 */
function guideToPrivacy() {
  uni.showModal({
    title: '需要同意隐私保护指引',
    content: '同意《用户隐私保护指引》后，才能使用相册功能。',
    confirmText: '查看指引',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm && (wx as any).openPrivacyContract)
        (wx as any).openPrivacyContract({})
    },
  })
}

export function chooseImageFile(options: ChooseImageOptions) {
  const msg = (err: any) => String(err?.errMsg || '')

  uni.chooseMedia({
    count: 1,
    mediaType: ['image'],
    sourceType: ['album', 'camera'],
    sizeType: ['compressed'],
    success: (res: any) => {
      const filePath = res?.tempFiles?.[0]?.tempFilePath
      if (filePath)
        options.onSelected(filePath)
    },
    fail: (err: any) => {
      const errMsg = msg(err)
      // 用户取消：静默
      if (errMsg.includes('cancel'))
        return
      // 相册/相机授权被拒：引导去设置
      if (errMsg.includes('auth deny') || errMsg.includes('authorize') || errMsg.includes('permission'))
        return guideToSettings()
      // 隐私协议未同意/未声明：引导查看指引
      if (errMsg.includes('privacy'))
        return guideToPrivacy()
      // 其他失败
      if (options.onFail)
        options.onFail()
      else
        toast('选择图片失败，请重试')
    },
  })
}
