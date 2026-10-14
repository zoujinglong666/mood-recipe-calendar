import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\test\java\com\moodrecipe\backend\controller\RecipeControllerTest.java')
c = f.read_text(encoding='utf-8')
old = 'when(agent.recommend(anyString(), anyString(), any())).thenReturn(expected);'
new = 'when(agent.recommend(anyString(), anyString(), any(), any())).thenReturn(expected);'
if old in c:
    c = c.replace(old, new)
    f.write_text(c, encoding='utf-8')
    print('fixed')
else:
    print('not found')
