import requests, re
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
r = requests.get('https://www.xiachufang.com/explore/', headers=headers, timeout=10)

# 菜名在 <p class="name"> 标签里
names = re.findall(r'<p class="name">\s*<a[^>]*>([^<]+)</a>', r.text)
print(f'找到 {len(names)} 个菜名:')
for i, n in enumerate(names[:20]):
    print(f'  {i+1}. {n.strip()}')
