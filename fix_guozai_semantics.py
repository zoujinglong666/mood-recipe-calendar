# -*- coding: utf-8 -*-
"""为 album / report 多页页面补全「语义匹配」的锅仔：
把整页复用 mood_01_happy 的区块，换成贴合该页内容的锅仔（满足/得意/庆祝），
消除「到处同一张开心脸」造成的语义缺失感。"""
import io
import os

base = os.path.dirname(os.path.abspath(__file__))
pages = os.path.join(base, 'mood-recipe-calendar', 'frontend', 'src', 'pages')

# (文件, {1-based行号: 目标png})
plan = {
    'report/index.vue': {
        203: 'mood_10_content.png',   # P2 年度总览 -> 满足
        228: 'mood_11_proud.png',     # P3 TOP3 -> 得意
        275: 'mood_11_proud.png',     # P5 热力图 -> 得意(为自己坚持)
        313: 'mood_10_content.png',   # P6 年度之最 -> 满足
        341: 'action_09_celebrate.png',  # P8 分享小图 -> 庆祝
        361: 'action_09_celebrate.png',  # P8 分享主图 -> 庆祝
    },
    'album/index.vue': {
        206: 'mood_10_content.png',   # 盘点 header 图标 -> 满足
        255: 'mood_10_content.png',   # 盘点底部主锅仔 -> 满足
        340: 'action_09_celebrate.png',  # 分享主锅仔 -> 庆祝
    },
}

for rel, lines_map in plan.items():
    fp = os.path.join(pages, rel)
    lines = io.open(fp, encoding='utf-8').read().split('\n')
    done = 0
    for ln, target in lines_map.items():
        idx = ln - 1
        if idx >= len(lines):
            print('  [跳过] %s L%d 不存在' % (rel, ln))
            continue
        line = lines[idx]
        if '/static/guozai/' not in line:
            print('  [跳过] %s L%d 无锅仔引用: %s' % (rel, ln, line.strip()[:40]))
            continue
        import re
        new = re.sub(r'/static/guozai/[a-z0-9_]+\.png', '/static/guozai/' + target, line)
        lines[idx] = new
        done += 1
    io.open(fp, 'w', encoding='utf-8', newline='').write('\n'.join(lines))
    print('%s: 替换 %d 处' % (rel, done))

# 校验残留
for rel in plan:
    fp = os.path.join(pages, rel)
    t = io.open(fp, encoding='utf-8').read()
    print('%s 剩余 mood_01_happy 引用数: %d' % (rel, t.count('mood_01_happy.png')))
