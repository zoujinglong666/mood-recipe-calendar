import { ref } from 'vue'

/**
 * 做菜语音指导（微信同声传译插件 WechatSI 的 TTS）。
 *
 * 场景痛点：做菜时手湿手油，摸不了屏幕——步骤朗读（切步骤自动播）、
 * 倒计时到点播报、最后一分钟预告，让用户"听着把菜做完"。
 *
 * 硬前提：
 * - 用户打开声音开关（storage 持久化，设置页/做菜页快捷开关共用）；
 * - 小程序后台已添加「同声传译」插件（manifest.config.ts 声明），
 *   未添加时 ttsAvailable=false，所有语音入口静默降级，绝不影响做菜主流程。
 *
 * 插件不可用的环境（H5/未声明/低版本基础库）：朗读按钮显示置灰态。
 */

/** 声音开关 storage key（设置页与做菜页快捷开关共用同一份状态） */
export const VOICE_GUIDE_KEY = 'mrc_voice_guide'

interface SiPlugin {
  /** 官方签名：textToSpeech({ content, lang, tts, success, fail })——参数名是 content 不是 text */
  textToSpeech: (opts: { content: string, lang?: string, tts?: boolean, success?: () => void, fail?: (err: any) => void }) => void
}

export function useCookingVoice() {
  /** 用户声音开关：默认关闭（语音是增强，不是默认行为） */
  const voiceEnabled = ref(false)
  /** 插件是否可用（后台添加了同声传译 + 小程序端） */
  const ttsAvailable = ref(false)
  /** 是否正在朗读（按钮态） */
  const speaking = ref(false)

  let plugin: SiPlugin | null = null
  let pluginTried = false

  function readSwitch() {
    try {
      voiceEnabled.value = Boolean(uni.getStorageSync(VOICE_GUIDE_KEY))
    }
    catch {
      voiceEnabled.value = false
    }
  }

  function setVoiceEnabled(on: boolean) {
    voiceEnabled.value = on
    try {
      uni.setStorageSync(VOICE_GUIDE_KEY, on)
    }
    catch {}
    if (!on)
      stopSpeak()
  }

  /** 懒加载插件：只在开关首次打开时初始化，避免冷启动开销 */
  function ensurePlugin(): SiPlugin | null {
    if (pluginTried)
      return plugin
    pluginTried = true
    // #ifdef MP-WEIXIN
    try {
      if (typeof requirePlugin === 'function') {
        const si = requirePlugin('WechatSI') as SiPlugin
        if (si && typeof si.textToSpeech === 'function') {
          plugin = si
          ttsAvailable.value = true
        }
      }
    }
    catch {
      // 未在后台添加插件 / 低版本基础库：静默降级
    }
    // #endif
    return plugin
  }

  /**
   * 朗读一段文本。自动打断上一段（同声传译插件本身会替换排队）。
   * 开关未开 / 插件不可用 / 空文本：静默返回（调用方无需分支）。
   */
  function speak(text: string) {
    if (!voiceEnabled.value || !text?.trim())
      return
    const si = ensurePlugin()
    if (!si)
      return
    speaking.value = true
    si.textToSpeech({
      content: text.trim(),
      lang: 'zh_CN',
      success: () => {
        speaking.value = false
      },
      fail: () => {
        // 插件偶发失败（如文本过长/配额）：重置态即可，不打扰用户
        speaking.value = false
      },
    })
  }

  function stopSpeak() {
    if (!plugin)
      return
    try {
      // 空文本朗读即打断当前播报
      plugin.textToSpeech({ content: '', lang: 'zh_CN' })
    }
    catch {}
    speaking.value = false
  }

  readSwitch()

  return {
    voiceEnabled,
    ttsAvailable,
    speaking,
    setVoiceEnabled,
    speak,
    stopSpeak,
    ensurePlugin,
  }
}

/** 倒计时播报文案：与页面 toast 文案一致，耳朵和眼睛给同一信息 */
export function timerFinishText(): string {
  return '计时结束，这一步完成啦'
}
export function timerOneMinuteText(): string {
  return '注意，还有一分钟'
}
