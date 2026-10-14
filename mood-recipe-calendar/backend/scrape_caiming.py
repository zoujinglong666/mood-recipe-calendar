import requests, re, time

headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# 多个分类页
categories = [
    ("热门", "https://www.xiachufang.com/explore/"),
    ("家常菜", "https://www.xiachufang.com/category/200101/"),
    ("早餐", "https://www.xiachufang.com/category/200102/"),
    ("午餐", "https://www.xiachufang.com/category/200103/"),
    ("晚餐", "https://www.xiachufang.com/category/200104/"),
    ("荤菜", "https://www.xiachufang.com/category/201000/"),
    ("素菜", "https://www.xiachufang.com/category/202000/"),
    ("汤羹", "https://www.xiachufang.com/category/203000/"),
    ("主食", "https://www.xiachufang.com/category/204000/"),
    ("烘焙", "https://www.xiachufang.com/category/205000/"),
    ("甜品", "https://www.xiachufang.com/category/206000/"),
]

def is_clean_name(name):
    """过滤标题党，只留干净菜名"""
    if len(name) < 2 or len(name) > 10:
        return False
    bad_chars = ['！', '❗', '🔥', '🥘', '🥔', '🍳', '😋', '～', '~', '…', '...', '：', ':', '，', ',', '？', '?', '✅', '㊙', '拿肉', '好吃', '香', '巨', '绝', '舔', '碗', '饭扫光']
    for c in bad_chars:
        if c in name:
            return False
    return True

all_names = set()

for cat_name, url in categories:
    try:
        r = requests.get(url, headers=headers, timeout=10)
        names = re.findall(r'<p class="name">\s*<a[^>]*>([^<]+)</a>', r.text)
        clean = [n.strip() for n in names if is_clean_name(n.strip())]
        all_names.update(clean)
        print(f"{cat_name}: 提取{len(names)}个, 干净{len(clean)}个, 累计{len(all_names)}")
        time.sleep(0.5)
    except Exception as e:
        print(f"{cat_name} 失败: {e}")

print(f"\n共 {len(all_names)} 个干净菜名:")
for i, n in enumerate(sorted(all_names)):
    print(f"  {i+1}. {n}")

with open("caiming_list.txt", "w", encoding="utf-8") as f:
    for n in sorted(all_names):
        f.write(n + "\n")
print(f"\n已保存 caiming_list.txt")
