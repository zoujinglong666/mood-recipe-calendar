# -*- coding: utf-8 -*-
"""将 frontend/src 下所有 margin/padding/gap 的 rpx 间距统一为 4 的倍数。
规则：v%4==0 不动；==1 -> -1；==2 -> +2；==3 -> +1
只处理 margin/padding/gap 及其 -top/-left 等子属性声明内的纯数字 rpx 值。
保留文件原始编码（含 BOM 状态）。
"""
import io, glob, re, os, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(ROOT, 'mood-recipe-calendar', 'frontend', 'src'))

decl_re = re.compile(r'(margin|padding|gap)(?:-[a-z]+)?\s*:\s*([^;}]+)')
num_re = re.compile(r'(\d+)rpx')

def fix(v):
    r = v % 4
    if r == 0:
        return v
    if r == 1:
        return v - 1
    return v + (4 - r)  # r in (2,3)

def fix_body(body):
    def rep(m):
        return f'{fix(int(m.group(1)))}rpx'
    return num_re.sub(rep, body)

def process(path):
    raw = open(path, 'rb').read()
    has_bom = raw.startswith(b'\xef\xbb\xbf')
    text = raw.decode('utf-8-sig')
    changed = 0
    def decl_sub(m):
        nonlocal changed
        new_body = fix_body(m.group(2))
        if new_body != m.group(2):
            changed += 1
        return m.group(1) + m.group(0)[len(m.group(0)) - len(m.group(0)):]  # placeholder
    # 用 sub 回调替换整个声明，保留属性名与冒号原文
    def repl(m):
        nonlocal changed
        new_body = fix_body(m.group(2))
        if new_body != m.group(2):
            changed += 1
        return f'{m.group(1)}:{m.group(2)[:0]}{new_body}'.replace('::', ':')
    # 上面 repl 处理冒号：m.group(0) 形如 "margin: 10rpx"，我们重构为 "margin" + 原文冒号区
    def repl2(m):
        nonlocal changed
        new_body = fix_body(m.group(2))
        if new_body != m.group(2):
            changed += 1
        prefix = m.group(0)[:m.group(0).find(m.group(2))]
        return prefix + new_body
    new_text = decl_re.sub(repl2, text)
    if new_text != text:
        out = (b'\xef\xbb\xbf' if has_bom else b'') + new_text.encode('utf-8')
        open(path, 'wb').write(out)
    return changed

total = 0
files_changed = []
for path in sorted(glob.glob(os.path.join(SRC, 'pages', '**', '*.vue'), recursive=True)) + \
              sorted(glob.glob(os.path.join(SRC, 'components', '**', '*.vue'), recursive=True)):
    c = process(path)
    if c:
        files_changed.append((os.path.relpath(path, SRC), c))
        total += c

for f, c in files_changed:
    print(f'{c:3d}  {f}')
print(f'TOTAL declarations fixed: {total} in {len(files_changed)} files')
