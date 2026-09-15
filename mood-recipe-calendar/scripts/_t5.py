# -*- coding: utf-8 -*-
import requests, time, re, json
UA_M = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
s = requests.Session()
s.headers.update({"User-Agent": UA_M, "Accept-Language": "zh-CN,zh;q=0.9"})
s.get("https://m.xiachufang.com/", timeout=20)
time.sleep(4)
r = s.get("https://m.xiachufang.com/recipe/100124682/", timeout=20)
html = r.text

# Look for embedded JSON state
for pat in [r'window\.__INITIAL_STATE__\s*=\s*(\{.*?\})\s*</script>',
            r'window\.__NUXT__\s*=\s*(\{.*?\})\s*</script>',
            r'__INITIAL_DATA__\s*=\s*(\{.*?\})\s*</script>',
            r'var\s+pageData\s*=\s*(\{.*?\});']:
    m = re.search(pat, html, re.S)
    if m:
        print("FOUND embedded:", pat[:40], "len", len(m.group(1)))
        break
else:
    print("no obvious embedded JSON")

# Check if content is in noscript or rendered
# Find where ingredients text actually is
idx = html.find("带皮五花肉")
print("\n'带皮五花肉' at index:", idx)
if idx > 0:
    print("context:", html[max(0,idx-200):idx+200])

# Check for __NEXT_DATA__ or similar
for tag in re.findall(r'<script[^>]*id="([^"]*)"[^>]*>', html):
    print("script id:", tag)
