# -*- coding: utf-8 -*-
"""用 wot-ui 组件库的 wd-navbar 替换自写 AppNav（第一批：无右侧功能页面）。"""
import os, io

SRC = r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\frontend\src\pages'

# 页面 -> (旧标签, 新标签, AppNav import 片段, import 是否独立成行)
PLAN = {
    'about': (
        '<AppNav title="关于锅仔" />',
        '<wd-navbar title="关于锅仔" left-arrow safe-area-inset-top @click-left="navBack" />',
        "import AppNav from '@/components/common/AppNav.vue'",
        True,
    ),
    'calendar': (
        '<AppNav :title="`${year}年${month}月`" />',
        '<wd-navbar :title="`${year}年${month}月`" left-arrow safe-area-inset-top @click-left="navBack" />',
        "import AppNav from '../../components/common/AppNav.vue'",
        True,
    ),
    'gallery': (
        '<AppNav title="锅仔形象馆" /> ',
        '<wd-navbar title="锅仔形象馆" left-arrow safe-area-inset-top @click-left="navBack" /> ',
        "import AppNav from '../../components/common/AppNav.vue'",
        True,
    ),
    'mood': (
        '<AppNav title="选一个心情吧" show-back />',
        '<wd-navbar title="选一个心情吧" left-arrow safe-area-inset-top @click-left="navBack" />',
        "import AppNav from '../../components/common/AppNav.vue'",
        True,
    ),
    'timeline': (
        '<AppNav title="菜谱时光机" show-back />',
        '<wd-navbar title="菜谱时光机" left-arrow safe-area-inset-top @click-left="navBack" />',
        "import AppNav from '../../components/common/AppNav.vue'",
        True,
    ),
    'feedback': (
        '<AppNav title="反馈建议" show-back/>',
        '<wd-navbar title="反馈建议" left-arrow safe-area-inset-top @click-left="navBack" />',
        "import AppNav from '../../components/common/AppNav.vue'",
        False,  # 内联在同一行
    ),
    'record': (
        '<AppNav title="记录今日伙食" />',
        '<wd-navbar title="记录今日伙食" safe-area-inset-top />',
        "import AppNav from '../../components/common/AppNav.vue'",
        True,  # tab 页，无返回箭头
    ),
}

for page, (old_tag, new_tag, old_import, standalone) in PLAN.items():
    fp = os.path.join(SRC, page, 'index.vue')
    t = io.open(fp, encoding='utf-8').read()
    changed = []

    # 1) 替换标签
    if old_tag in t:
        t = t.replace(old_tag, new_tag)
        changed.append('tag')
    else:
        print(f'[WARN] {page}: tag not found -> {old_tag!r}')

    # 2) 删除 AppNav import
    if standalone:
        # 独立行：删整行（含行尾换行）
        import re
        pat = re.compile(r'[ \t]*import AppNav from \'[^\']+\'\r?\n')
        t2 = pat.sub('', t, count=1)
        if t2 != t:
            t = t2
            changed.append('import-line')
        else:
            print(f'[WARN] {page}: standalone import not matched')
    else:
        # 内联：删 'import AppNav from "..."; ' 片段
        seg = f"import AppNav from '{old_import.split(' from ')[1].strip(chr(39))}'; "
        # 用完整片段构造
        full = f"import AppNav from \"../../components/common/AppNav.vue\"; "
        full = full.replace('"', "'")
        if full in t:
            t = t.replace(full, '')
            changed.append('import-inline')
        else:
            # 兜底：匹配单引号/双引号任意路径
            import re
            t2 = re.sub(r"import AppNav from ['\"][^'\"]+['\"]; ?", '', t, count=1)
            if t2 != t:
                t = t2
                changed.append('import-inline-re')
            else:
                print(f'[WARN] {page}: inline import not matched')

    # 3) 插入 navBack import（紧跟 <script setup lang="ts"> 之后）
    anchor = '<script setup lang="ts">'
    nav_import = "import { navBack } from '@/composables/useNavBar'"
    if anchor in t and nav_import not in t:
        t = t.replace(anchor, anchor + '\n' + nav_import, 1)
        changed.append('nav-import')
    elif nav_import not in t:
        print(f'[WARN] {page}: no script anchor')

    io.open(fp, 'w', encoding='utf-8', newline='\n').write(t)
    print(f'{page}: changed={changed}')
