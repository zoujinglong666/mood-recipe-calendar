import pathlib
d = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\resources')
for f in sorted(d.glob('*.psv')):
    lines = f.read_text(encoding='utf-8').splitlines()
    print(f'{f.name}: {len(lines)} lines, {f.stat().st_size} bytes')
print('=== v1 first 3 lines ===')
v1 = (d/'recipe-catalog-v1.psv').read_text(encoding='utf-8')
for i, line in enumerate(v1.splitlines()[:3]):
    parts = line.split('|')
    name = parts[0][:40] if parts else '?'
    print(f'Line {i}: {len(parts)} fields, name={name}')
