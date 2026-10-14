import pathlib, re
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\gen_recipes.py')
lines = f.read_text(encoding='utf-8').splitlines()
fixed = []
for line in lines:
    # Fix lines like  'xxx')  that should be  'xxx'])
    stripped = line.rstrip()
    if stripped.endswith("')") and not stripped.endswith("'])"):
        stripped = stripped[:-2] + "'])"
    fixed.append(stripped)
f.write_text('\n'.join(fixed), encoding='utf-8')
print('done')
