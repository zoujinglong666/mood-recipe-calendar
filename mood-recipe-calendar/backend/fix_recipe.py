import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend\entity\Recipe.java')
c = f.read_text(encoding='utf-8')

old = '''    @Column(name = "mood_tags")
    private String moodTags;

    private String season;'''
new = '''    @Column(name = "mood_tags")
    private String moodTags;

    /** 口味：清淡/辣/甜/酸/鲜等，逗号分隔。 */
    private String taste;

    /** 餐别：lunch/dinner/all。 */
    @Column(name = "meal_type")
    private String mealType;

    /** 标签：快手菜/汤品/主食等，逗号分隔。 */
    private String tags;

    private String season;'''
if old in c:
    c = c.replace(old, new)
    print('OK: Recipe entity updated')
else:
    print('NOT FOUND')

f.write_text(c, encoding='utf-8')
