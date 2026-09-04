# -*- coding: utf-8 -*-
"""给所有页面 wd-navbar 添加透明背景 custom-style，让页面背景延续到顶部导航栏。"""
import io
import re
import glob
import os

base = os.path.dirname(os.path.abspath(__file__))
frontend = os.path.join(base, 'mood-recipe-calendar', 'frontend')

CSS = 'background-color: transparent !important;'
changed = []

for f in glob.glob(os.path.join(frontend, 'src', 'pages', '*', 'index.vue')):
    t = io.open(f, encoding='utf-8').read()
    n = [0]

    def repl(m):
        tag = m.group(0)
        if 'custom-style' in tag:
            return tag
        n[0] += 1
        return tag[:-2] + ' custom-style="' + CSS + '" />'

    out = re.sub(r'<wd-navbar\b[^>]*?/>', repl, t)
    if n[0]:
        io.open(f, 'w', encoding='utf-8', newline='').write(out)
        changed.append((os.path.relpath(f, frontend), n[0]))

for f, c in changed:
    print(c, f)
print('total files:', len(changed))
