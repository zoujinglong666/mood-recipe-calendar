import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'

interface ShareOptions {
  /** 转发/朋友圈标题：字符串，或返回字符串的函数（便于带页面动态内容，如菜名/日期） */
  title?: string | (() => string)
  /** 转发卡片预览图（朋友圈不支持 imageUrl，仅转发可用） */
  imageUrl?: string | (() => string)
}

/**
 * 在需要可转发的页面 <script setup> 内调用一次即可。
 * 自动取当前页 route 拼 path，并带上 sharer=openid 归因，
 * 让微信胶囊「···」里的「转发」与「分享到朋友圈」可用。
 */
export function useShare(options: ShareOptions = {}) {
  const userStore = useUserStore()
  const defaultTitle = '锅仔 · 按心情帮你决定今天吃什么'
  const resolve = (v?: string | (() => string)) => (typeof v === 'function' ? v() : v) || ''

  onShareAppMessage(() => {
    const pages = getCurrentPages()
    const current = pages[pages.length - 1] as unknown as { route?: string }
    const route = current?.route || ''
    const sharer = userStore.openid
    const path = `/${route}${sharer ? `?sharer=${encodeURIComponent(sharer)}` : ''}`
    const imageUrl = resolve(options.imageUrl)
    return {
      title: resolve(options.title) || defaultTitle,
      path,
      ...(imageUrl ? { imageUrl } : {}),
    }
  })

  onShareTimeline(() => {
    const sharer = userStore.openid
    return {
      title: resolve(options.title) || defaultTitle,
      query: sharer ? `sharer=${encodeURIComponent(sharer)}` : '',
    }
  })
}
