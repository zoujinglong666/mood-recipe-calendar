/**
 * 微信流量主广告位 ID（mp.weixin.qq.com 流量主后台创建）。
 * 后台下线/更换广告位时只改这里，业务调用方无感。
 *
 * 注意：激励视频的「看完发奖」必须走服务端校验（客户端回调可伪造），
 * 前端 hook 只负责播放生命周期，发奖逻辑在 api 层。
 */
export const AD_UNITS = {
  /** 通用额度广告：免费额度/对话券耗尽后「看广告 +1 次」的激励视频 */
  QUOTA_REWARD: 'adunit-ec548835ad032028',
  /** 冰箱页智能拍照识别：免费识别次数用完后的激励视频 */
  FRIDGE_OCR: 'adunit-2989a23e0aa6f93e',
  /** 首页视频广告 */
  HOME_VIDEO: 'adunit-d1fbe96ca057f7e8',
} as const
