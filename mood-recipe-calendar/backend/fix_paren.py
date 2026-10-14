import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\gen_recipes.py')
c = f.read_text(encoding='utf-8')
c = c.replace("'挤番茄酱')", "'挤番茄酱'])")
f.write_text(c, encoding='utf-8')
print('fixed')
