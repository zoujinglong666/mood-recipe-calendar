/**
 * 构建后补丁：向 mp-weixin 产物的 app.json 注入微信小程序必填字段
 * - __usePrivacyCheck__: 开启隐私保护指引检查
 * - requiredPrivateInfos: 声明使用的隐私接口
 * 原因：@uni-helper/vite-plugin-uni-manifest 未透传这两个字段到 app.json
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
