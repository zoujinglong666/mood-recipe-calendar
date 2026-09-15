/**
 * 构建后补丁：向 mp-weixin 产物的 app.json 注入微信小程序必填字段
 * - __usePrivacyCheck__: 开启隐私保护指引检查
 * - requiredPrivateInfos: 声明使用的隐私接口
 * 原因：@uni-helper/vite-plugin-uni-manifest 未透传这两个字段到 app.json
 *
 * 同时做图标字体内联：
 * - wot-ui 的 wd-icon 字体默认走 https://at.alicdn.com 远程 URL，
 *   微信小程序 @font-face 不支持远程字体（会加载失败导致图标空白），
 *   这里把 scripts/wot-icon-font.ttf 转 base64 内联进 wd-icon.wxss。
 */
const fs = require('fs')
const path = require('path')

const appJsonPath = path.resolve(__dirname, '../dist/build/mp-weixin/app.json')

if (!fs.existsSync(appJsonPath)) {
  console.error('[patch-mp-weixin] app.json 不存在，跳过:', appJsonPath)
  process.exit(0)
}

const appJson = JSON.parse(fs.readFileSync(appJsonPath, 'utf-8'))

appJson.__usePrivacyCheck__ = true
// 清理非法残留：requiredPrivateInfos 只接受位置/蓝牙/地址类接口，相册由隐私保护指引管理
delete appJson.requiredPrivateInfos

fs.writeFileSync(appJsonPath, JSON.stringify(appJson, null, 2) + '\n', 'utf-8')
console.log('[patch-mp-weixin] 已注入 __usePrivacyCheck__，并清理 requiredPrivateInfos 残留')

// ---------- 图标字体内联 ----------
const fontPath = path.resolve(__dirname, './wot-icon-font.ttf')
const iconWxssPath = path.resolve(__dirname, '../dist/build/mp-weixin/node-modules/@wot-ui/ui/components/wd-icon/wd-icon.wxss')

if (fs.existsSync(fontPath) && fs.existsSync(iconWxssPath)) {
  const base64 = fs.readFileSync(fontPath).toString('base64')
  const wxss = fs.readFileSync(iconWxssPath, 'utf-8')
  // 替换 @font-face 为纯 base64 内联（仅保留 truetype）
  const inlined = wxss.replace(
    /@font-face\{font-family:wd-icons;.*?\}/,
    `@font-face{font-family:wd-icons;src:url(data:font/truetype;charset=utf-8;base64,${base64}) format("truetype")}`
  )
  if (inlined !== wxss) {
    fs.writeFileSync(iconWxssPath, inlined, 'utf-8')
    console.log(`[patch-mp-weixin] wd-icon 字体内联完成（${(base64.length / 1024).toFixed(1)} KB base64）`)
  }
  else {
    console.error('[patch-mp-weixin] 未匹配到 wd-icon @font-face，字体内联失败')
  }
}
else {
  console.error('[patch-mp-weixin] 字体或 wd-icon.wxss 缺失，跳过字体内联:', fontPath, fs.existsSync(iconWxssPath))
}
