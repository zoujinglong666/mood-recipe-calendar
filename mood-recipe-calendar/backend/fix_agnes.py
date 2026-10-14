import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\test\java\com\moodrecipe\backend\service\AgnesRecipeImageServiceTest.java')
c = f.read_text(encoding='utf-8')
old = 'assertTrue(body.get("prompt").toString().contains("只出现完成后的菜品"));'
new = 'assertTrue(body.get("prompt").toString().contains("只出现完成后的这道菜"));'
if old in c:
    c = c.replace(old, new)
    f.write_text(c, encoding='utf-8')
    print('fixed')
else:
    print('not found')
