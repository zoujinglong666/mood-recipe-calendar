# -*- coding: utf-8 -*-
"""
crawl_recipes.py — 从下厨房串行爬取真实菜谱，生成 recipe-catalog-v2.psv

反爬策略：
  - 用 curl.exe 发起请求（TLS 指纹与 Python requests 不同，绕过特征识别）
  - 先 GET 首页建立会话（cookie jar），全程串行
  - 每请求随机 sleep 5-10 秒
  - 遇"滑动验证"或过短页面 -> 退避 15-30s 刷新首页，最多重试 4 次
  - 图片一律保留 i2.chuimg.com 网络 URL，不下载本地

输出：
  scripts/crawled_recipes.json      —— 断点续跑缓存
  scripts/collected_ids.json        —— 搜索阶段收集的 ID 缓存
  backend/src/main/resources/recipe-catalog-v2.psv

用法：
  python scripts/crawl_recipes.py --target 160
"""
import argparse
import json
import random
import re
import subprocess
import time
from pathlib import Path
from urllib.parse import quote

from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path(__file__).resolve().parent / "crawled_recipes.json"
ID_CACHE = Path(__file__).resolve().parent / "collected_ids.json"
COOKIE_JAR = Path(__file__).resolve().parent / "_cookies.txt"
PSV_OUT = ROOT / "backend" / "src" / "main" / "resources" / "recipe-catalog-v2.psv"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
HOME = "https://www.xiachufang.com/"
SEARCH = "https://www.xiachufang.com/search/"
RECIPE_URL = "https://www.xiachufang.com/recipe/{rid}/"

VALID_MOODS = {"开心", "平静", "疲惫", "焦虑", "难过", "嘴馋",
               "低落", "想家", "期待", "满足", "得意", "害羞"}

KEYWORDS = [
    "家常菜", "快手菜", "下饭菜", "汤羹", "炒菜", "炖菜", "蒸菜",
    "红烧肉", "凉拌菜", "汤面", "蛋炒饭", "饺子", "包子",
    "排骨", "鸡腿", "红烧鱼", "麻婆豆腐", "番茄炒蛋", "土豆丝",
    "茄子", "西兰花", "小米粥", "葱油拌面", "蒸蛋", "冬瓜汤",
    "排骨汤", "可乐鸡翅", "宫保鸡丁", "鱼香肉丝", "清炒时蔬",
    "番茄牛腩", "酸辣土豆丝", "清蒸鱼", "白灼虾",
    "紫菜蛋花汤", "南瓜粥", "葱油饼", "蒸南瓜", "蒜蓉西兰花",
    "香菇滑鸡", "红烧豆腐", "鸡蛋羹", "肉末茄子", "番茄豆腐汤",
    "葱花鸡蛋饼", "萝卜汤", "炖排骨", "炒青菜",
]


# ---------- curl HTTP layer ----------
def curl_get(url, referer=None, timeout=25):
    """用 curl.exe 发起 GET，返回 HTML 文本或 None。不用 cookie jar。"""
    cmd = [
        "curl.exe", "-s", "-L",
        "--max-time", str(timeout),
        "-A", UA,
        "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "-H", "Accept-Language: zh-CN,zh;q=0.9",
    ]
    if referer:
        cmd += ["-H", f"Referer: {referer}"]
    cmd.append(url)
    try:
        r = subprocess.run(cmd, capture_output=True, timeout=timeout + 10)
        return r.stdout.decode("utf-8", errors="replace")
    except Exception as e:
        print(f"  [curl err] {e}", flush=True)
        return None


def polite_sleep(lo=10.0, hi=15.0):
    time.sleep(random.uniform(lo, hi))


def is_blocked(text, length):
    if length < 6000 or length == 8:
        return True
    if "滑动验证" in text:
        return True
    return False


def fetch_page(url, referer=None, max_retry=4):
    for attempt in range(max_retry):
        polite_sleep()
        text = curl_get(url, referer=referer)
        if text is not None and not is_blocked(text, len(text)):
            return text
        print(f"  [blocked] attempt {attempt+1} len={len(text) if text else 0} url={url}", flush=True)
        wait = random.uniform(15, 30)
        print(f"  [backoff] sleep {wait:.1f}s then refresh home", flush=True)
        time.sleep(wait)
        curl_get(HOME, referer=None)
    return None


# ---------- mood / difficulty ----------
def mood_tags(name, desc):
    text = name + " " + desc
    tags = []
    def add(*xs):
        for t in xs:
            if t not in tags and t in VALID_MOODS:
                tags.append(t)
    if re.search(r"红烧|火锅|烤肉|可乐鸡翅|糖醋|回锅肉|扣肉|东坡|卤肉|卤味", text):
        add("嘴馋", "满足", "得意")
    elif re.search(r"蛋糕|甜品|曲奇|面包|布丁|蛋挞|泡芙|奶茶", text):
        add("开心", "嘴馋", "期待")
    elif re.search(r"粥|小米粥|南瓜粥|白粥|汤面|面线|馄饨|汤粉|热汤", text):
        add("平静", "疲惫", "满足")
    elif re.search(r"汤|羹|炖|煲", text):
        add("满足", "平静", "疲惫")
    elif re.search(r"蒸|清蒸|白灼|清炒|水煮|凉拌|沙拉", text):
        add("平静", "满足")
    elif re.search(r"炒饭|炒面|炒粉|盖饭", text):
        add("满足", "嘴馋")
    elif re.search(r"饺子|包子|馒头|花卷|葱油饼|饼", text):
        add("想家", "满足", "平静")
    elif re.search(r"蛋|豆腐|青菜|时蔬|西兰花|土豆|茄子", text):
        add("平静", "满足")
    else:
        add("平静", "满足", "嘴馋")
    if re.search(r"家乡|妈妈|外婆|老家|地道", text):
        add("想家")
    tags = tags[:3]
    if not tags:
        tags = ["平静", "满足"]
    return ",".join(tags)


def estimate_minutes(name, steps):
    if re.search(r"粥|汤|炖|煲|红烧|卤|牛腩|排骨", name):
        m = 60 + steps * 5
    elif re.search(r"蒸|清蒸", name):
        m = 25 + steps * 4
    elif re.search(r"凉拌", name):
        m = 10 + steps * 3
    else:
        m = 15 + steps * 3
    if steps <= 3 and not re.search(r"炖|煲|红烧|粥|汤", name):
        m = min(m, 25)
    return max(5, min(180, int(m)))


def estimate_difficulty(name, steps):
    if steps >= 6 or re.search(r"炖|烤|油炸|烘焙|蛋糕", name):
        return "中等"
    return "简单"


# ---------- parse ----------
IMG_RE = re.compile(r'https://i[12]\.chuimg\.com/[^"\'\s\\]+\.(?:jpg|png)')


def normalize_img(url):
    return re.sub(r'_\d+w_\d+h\.', '_640w_960h.', url)


def parse_recipe(rid, html):
    soup = BeautifulSoup(html, "lxml")
    h1 = soup.find("h1")
    if not h1:
        return None
    name = h1.get_text(strip=True)
    if not name:
        return None

    imgs = list(dict.fromkeys(IMG_RE.findall(html)))
    # 过滤 CSS 背景图（含括号或 .css 特征）
    imgs = [i for i in imgs if "{" not in i and "}" not in i]
    if not imgs:
        return None
    image = normalize_img(imgs[0])

    ings = []
    ings_div = soup.select_one("div.ings")
    if ings_div:
        for tr in ings_div.select("tr"):
            nm_el = tr.select_one("td.name")
            unit_el = tr.select_one("td.unit")
            nm = nm_el.get_text(" ", strip=True) if nm_el else ""
            unit = unit_el.get_text(" ", strip=True) if unit_el else ""
            nm = re.sub(r"\s+", " ", nm).strip()
            unit = re.sub(r"\s+", " ", unit).strip()
            if not nm:
                continue
            ings.append(f"{nm} {unit}".strip() if unit else nm)
    if len(ings) < 3:
        return None

    steps = [p.get_text(strip=True) for p in soup.select("div.steps li p")]
    steps = [re.sub(r"\s+", " ", s).strip() for s in steps if s.strip()]
    if len(steps) < 3 or len(steps) > 30:
        return None

    desc = ""
    intro = soup.select_one("div.desc, p")
    if intro:
        desc = re.sub(r"\s+", " ", intro.get_text(" ", strip=True)).strip()
    if not desc or len(desc) < 8:
        desc = f"{name}，家常做法，步骤清晰易上手。"
    desc = desc[:120]

    return {
        "rid": rid, "name": name, "description": desc, "image": image,
        "ingredients": ings, "steps": steps,
        "cooking_time": estimate_minutes(name, len(steps)),
        "difficulty": estimate_difficulty(name, len(steps)),
        "mood_tags": mood_tags(name, desc), "season": "四季",
    }


# ---------- cache ----------
def load_cache():
    if CACHE.exists():
        with open(CACHE, "r", encoding="utf-8") as f:
            return json.load(f)
    return []


def save_cache(recipes):
    with open(CACHE, "w", encoding="utf-8") as f:
        json.dump(recipes, f, ensure_ascii=False, indent=2)


# ---------- main ----------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", type=int, default=160)
    ap.add_argument("--max-pages", type=int, default=1)
    args = ap.parse_args()

    recipes = load_cache()
    done_ids = {r["rid"] for r in recipes}
    done_names = {r["name"] for r in recipes}
    print(f"[resume] cache has {len(recipes)} recipes", flush=True)

    # 预热：先访问首页（curl 无 cookie jar，靠 TLS 指纹绕过）
    curl_get(HOME)
    print("[home] warmed up", flush=True)
    time.sleep(3)

    failed_ids = []

    # Phase 1: 收集 ID（用 curl，不容易触发指纹封禁）
    if ID_CACHE.exists():
        with open(ID_CACHE, "r", encoding="utf-8") as f:
            all_ids = json.load(f)
        print(f"[phase1] cached {len(all_ids)} ids", flush=True)
    else:
        all_ids = []
        seen = set()
        for kw in KEYWORDS:
            for page in range(1, args.max_pages + 1):
                text = fetch_page(SEARCH + f"?keyword={quote(kw)}", referer=HOME)
                if text is None:
                    break
                found = [int(x) for x in dict.fromkeys(re.findall(r"/recipe/(\d+)/", text)) if int(x) > 10000]
                new = [i for i in found if i not in seen]
                seen.update(new)
                all_ids.extend(new)
            print(f"  [search] {kw}: total {len(all_ids)}", flush=True)
            time.sleep(random.uniform(6, 10))
        with open(ID_CACHE, "w", encoding="utf-8") as f:
            json.dump(all_ids, f)

    # Phase 2: 抓详情
    print(f"[phase2] fetching details from {len(all_ids)} ids", flush=True)
    for rid in all_ids:
        if len(recipes) >= args.target:
            break
        if rid in done_ids:
            continue
        html = fetch_page(RECIPE_URL.format(rid=rid), referer=HOME)
        if html is None:
            failed_ids.append(rid)
            print(f"  [skip] rid={rid} (blocked)", flush=True)
            time.sleep(random.uniform(30, 60))
            continue
        parsed = parse_recipe(rid, html)
        if parsed is None:
            failed_ids.append(rid)
            print(f"  [skip] rid={rid} (parse incomplete)", flush=True)
            continue
        if parsed["name"] in done_names:
            continue
        recipes.append(parsed)
        done_ids.add(rid)
        done_names.add(parsed["name"])
        save_cache(recipes)
        print(f"  [ok] {rid} {parsed['name']} ings={len(parsed['ingredients'])} steps={len(parsed['steps'])}", flush=True)
        if len(recipes) % 10 == 0:
            brk = random.uniform(30, 50)
            print(f"  [rest] {brk:.0f}s", flush=True)
            time.sleep(brk)

    # 图片 HEAD 自检
    print("\n=== image check ===", flush=True)
    bad_imgs = []
    for r in recipes:
        try:
            resp = subprocess.run(
                ["curl.exe", "-s", "-o", "NUL", "-w", "%{http_code}",
                 "-A", UA, "--max-time", "10", r["image"]],
                capture_output=True, timeout=15)
            code = resp.stdout.decode().strip()
            if code != "200":
                bad_imgs.append((r["rid"], r["image"], code))
        except Exception as e:
            bad_imgs.append((r["rid"], r["image"], str(e)))
    print(f"  bad images: {len(bad_imgs)}", flush=True)
    for b in bad_imgs[:20]:
        print("   ", b, flush=True)

    write_psv(recipes)
    print(f"\n[done] total: {len(recipes)}", flush=True)
    if failed_ids:
        print(f"[warn] failed: {failed_ids}", flush=True)


def write_psv(recipes):
    PSV_OUT.parent.mkdir(parents=True, exist_ok=True)
    lines = ["# name|moods|minutes|difficulty|description|image|ingredients-json|steps-json\n"]
    for r in recipes:
        fields = [
            r["name"].replace("|", " "),
            r["mood_tags"],
            str(r["cooking_time"]),
            r["difficulty"],
            r["description"].replace("|", " "),
            r["image"],
            json.dumps(r["ingredients"], ensure_ascii=False),
            json.dumps(r["steps"], ensure_ascii=False),
        ]
        lines.append("|".join(fields) + "\n")
    with open(PSV_OUT, "w", encoding="utf-8", newline="\n") as f:
        f.writelines(lines)


if __name__ == "__main__":
    main()
