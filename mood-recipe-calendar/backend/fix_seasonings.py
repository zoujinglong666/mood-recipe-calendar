import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend\entity\Recipe.java')
c = f.read_text(encoding='utf-8')

old = '''    /** 标签：快手菜/汤品/主食等，逗号分隔。 */
    private String tags;

    private String season;'''
new = '''    /** 标签：快手菜/汤品/主食等，逗号分隔。 */
    private String tags;

    /** 调料：盐/酱油/醋等，跟食材分开，逗号分隔。 */
    private String seasonings;

    private String season;'''
if old in c:
    c = c.replace(old, new)
    print('OK: seasonings added')
else:
    print('NOT FOUND')

f.write_text(c, encoding='utf-8')
