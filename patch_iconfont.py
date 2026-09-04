# -*- coding: utf-8 -*-
"""把 wot-ui 的 iconfont.scss 中第一个 @font-face（阿里 CDN）替换为 base64 内联，
解决微信小程序端 CDN 域名白名单导致 wd-icon 图标不显示的问题。

背景：
- wot-ui 的 wd-icon 图标字体默认指向 https://at.alicdn.com/...（CDN）。
- 微信小程序端 @font-face 只支持「配置了合法域名的网络字体」或「base64 内联」，
  未配置该 CDN 域名时图标字体加载失败 -> 所有 wd-icon-* 图标空白（H5 端正常）。
- 本脚本把本地 iconfont.ttf 以 base64 内联写入 @font-face，彻底绕开域名限制。

用法（在仓库根目录执行，需已 pnpm install）：
    python patch_iconfont.py
重装依赖（node_modules 被还原）后需重新执行一次。
"""
import io
import os
import base64

base = os.path.dirname(os.path.abspath(__file__))
frontend = os.path.join(base, 'mood-recipe-calendar', 'frontend')

TTF = os.path.join(frontend, 'node_modules', '@wot-ui', 'ui', 'components', 'wd-icon', 'iconfont.ttf')
SCSS = os.path.join(frontend, 'node_modules', '@wot-ui', 'ui', 'components', 'wd-icon', 'iconfont.scss')

if not os.path.exists(TTF):
    raise SystemExit(f'[ERR] 未找到 iconfont.ttf: {TTF}\n请确认已执行 pnpm install。')

b64 = base64.b64encode(open(TTF, 'rb').read()).decode()
src = io.open(SCSS, encoding='utf-8').read()

start = src.find('@font-face')
end = src.find('}', start) + 1
new_face = (
    '@font-face {\n'
    '  font-family: "wd-icons";\n'
    '  /* base64 内联，避免小程序端 CDN 域名限制导致图标不显示 */\n'
    '  src: url(data:font/truetype;charset=utf-8;base64,' + b64 + ') format("truetype");\n'
    '}'
)
out = src[:start] + new_face + src[end:]
io.open(SCSS, 'w', encoding='utf-8', newline='\n').write(out)
print('注入完成。')
print('  原 CDN 引用已移除:', 'alicdn' not in out)
print('  已内联 base64 字体:', 'data:font' in out)
print('  node_modules 下的 iconfont.scss 已更新，重新构建即可生效。')
