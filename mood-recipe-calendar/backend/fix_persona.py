import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend\service\GuozaiPersona.java')
c = f.read_text(encoding='utf-8')

old = '.append("请结合以上分析和心情，推荐一道适合此刻的中国家常菜。")\n                .append("如果用户最近总吃类似的菜，可以适当换个口味；如果用户情绪低落，推荐暖胃治愈的菜。\\n")'

new = '.append("请结合以上分析和心情，推荐一道适合此刻的中国家常菜。")\n                .append("如果用户最近总吃类似的菜，可以适当换个口味；如果用户情绪低落，推荐暖胃治愈的菜。\\n")\n                .append("【重要】优先从已有真实菜谱做变体创新（例如：番茄炒蛋的少油版/加虾仁版，宫保鸡丁的核桃版，麻婆豆腐的嫩牛版），")\n                .append("保留原菜核心风味，只在食材搭配、烹饪方式或分量上做合理调整，不要凭空发明奇怪的菜。\\n")'

if old in c:
    c = c.replace(old, new)
    f.write_text(c, encoding='utf-8')
    print('OK: added variant instruction')
else:
    print('NOT FOUND - checking...')
    # Find the line
    for i, line in enumerate(c.splitlines(), 1):
        if '暖胃治愈的菜' in line:
            print(f'Line {i}: {line.strip()}')
