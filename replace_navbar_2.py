# -*- coding: utf-8 -*-
"""wd-navbar 替换第二批：index / album / recipe / report（有右侧功能页）。"""
import os, io, re

SRC = r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\frontend\src\pages'

PLAN = {
    'index': dict(
        old='<AppNav title="心情菜谱日历" @nav-right="goto(\'profile\')" />',
        new='<wd-navbar title="心情菜谱日历" safe-area-inset-top />',
        add=[],
    ),
    'album': dict(
        old='<AppNav title="月度画册" right-icon="share" @nav-right="onShare" />',
        new='<wd-navbar title="月度画册" left-arrow safe-area-inset-top @click-left="navBack" />',
        add=['navBack', 'Icon'],
    ),
    'recipe': dict(
        old='<AppNav title="AI 今日推荐" right-icon="share" @nav-right="onShare" />',
        new='<wd-navbar title="AI 今日推荐" left-arrow safe-area-inset-top @click-left="navBack" />',
        add=['navBack'],
    ),
    'report': dict(
        old='<AppNav title="年度报告" show-back right-icon="share" />',
        new='<wd-navbar title="年度报告" left-arrow safe-area-inset-top @click-left="navBack" />',
        add=['navBack', 'Icon'],
    ),
}

NAV_IMPORT = "import { navBack } from '@/composables/useNavBar'"
ICON_IMPORT = "import Icon from '../../components/common/Icon.vue'"

for page, cfg in PLAN.items():
    fp = os.path.join(SRC, page, 'index.vue')
    t = io.open(fp, encoding='utf-8').read()
    changed = []

    # 1) 替换标签
    if cfg['old'] in t:
        t = t.replace(cfg['old'], cfg['new'])
        changed.append('tag')
    else:
        print(f'[WARN] {page}: tag not found')

    # 2) 删除 AppNav import（独立行）
    t2 = re.sub(r"[ \t]*import AppNav from ['\"][^'\"]+['\"]\r?\n", '', t, count=1)
    if t2 != t:
        t = t2
        changed.append('import-line')
    else:
        print(f'[WARN] {page}: AppNav import not matched')

    # 3) 追加 import（紧跟 <script setup lang="ts"> 之后，保持顺序）
    anchor = '<script setup lang="ts">'
    inject = []
    if 'navBack' in cfg['add'] and NAV_IMPORT not in t:
        inject.append(NAV_IMPORT)
    if 'Icon' in cfg['add'] and ICON_IMPORT not in t:
        inject.append(ICON_IMPORT)
    if inject and anchor in t:
        t = t.replace(anchor, anchor + '\n' + '\n'.join(inject), 1)
        changed.append('+import:' + ','.join(inject))
    elif inject:
        print(f'[WARN] {page}: no script anchor for imports')

    io.open(fp, 'w', encoding='utf-8', newline='\n').write(t)
    print(f'{page}: changed={changed}')
