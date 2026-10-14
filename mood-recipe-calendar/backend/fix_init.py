import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\sql\init.sql')
c = f.read_text(encoding='utf-8')
old = "  too_hard    TINYINT(1) COMMENT '\u662f\u5426\u592a\u96be\u505a',"
new = "  too_hard    TINYINT(1) COMMENT '\u662f\u5426\u592a\u96be\u505a',\n  mood_after   VARCHAR(16) COMMENT '\u505a\u540e\u7684\u5fc3\u60c5',"
if old in c:
    c = c.replace(old, new)
    f.write_text(c, encoding='utf-8')
    print('added mood_after')
else:
    print('not found')
