# -*- coding: utf-8 -*-
import re, requests, time

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
HEADERS = {
    "User-Agent": UA,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Upgrade-Insecure-Requests": "1",
}
s = requests.Session()
s.headers.update(HEADERS)
s.get("https://www.xiachufang.com/", timeout=20)
time.sleep(2)

for url in ["https://www.xiachufang.com/recipe/104493058/",
            "https://www.xiachufang.com/recipe/1000357/"]:
    r = s.get(url, headers={"Referer": "https://www.xiachufang.com/search/?keyword=cook"}, timeout=20)
    print("="*30, url, r.status_code, len(r.text))
    # strip tags for a readable text preview
    txt = re.sub(r"<script.*?</script>", " ", r.text, flags=re.S)
    txt = re.sub(r"<style.*?</style>", " ", txt, flags=re.S)
    txt = re.sub(r"<[^>]+>", " ", txt)
    txt = re.sub(r"\s+", " ", txt).strip()
    print(txt[:600])
    time.sleep(2)
