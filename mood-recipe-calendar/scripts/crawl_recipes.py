# -*- coding: utf-8 -*-
"""
crawl_recipes.py — 从下厨房串行爬取真实菜谱，生成 recipe-catalog-v2.psv

严格反爬：
  1) 先 GET 首页拿 bid cookie，全程复用同一 Session
  2) 全程串行，每请求随机 sleep 2-4 秒
  3) 遇“滑动验证”或页面过短 -> 退避 15-30s、刷新首页 cookie、最多重试 4 次
  4) 图片一律保留 i2.chuimg.com 网络 URL，不下载本地

输出：
  scripts/crawled_recipes.json   —— 断点续跑缓存（已成功解析的菜谱）
  backend/src/main/resources/recipe-catalog-v2.psv —— 8 字段管道分隔

用法：
  python scripts/crawl_recipes.py            # 续跑，直到收集到 TARGET 道
  python scripts/crawl_recipes.py --target 160
"""
import argparse
import json
import os
import random
import re
import sys
import time
from pathlib import Path

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path(__file__).resolve().parent / "crawled_recipes.json"
PSV_OUT = ROOT / "backend" / "src" / "main" / "resources" / "recipe-catalog-v2.psv"

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
HOME = "https://www.xiachufang.com/"
SEARCH = "https://www.xiachufang.com/search/"
RECIPE_URL = "https://www.xiachufang.com/recipe/{rid}/"

VALID_MOODS = {"开心", "平静", "疲惫", "焦虑", "难过", "嘴馋",
               "低落", "想家", "期待", "满足", "得意", "害羞"}

# 采集关键词：覆盖家常菜/快手/汤/炖/蒸/凉拌/主食/甜品小吃
KEYWORDS = [
    "家常菜", "快手菜", "下饭菜", "汤羹", "炒菜", "炖菜", "蒸菜",
    "红烧肉", "凉拌菜", "汤面", "蛋炒饭", "饺子", "包子",
    "排骨", "鸡腿", "红烧鱼", "麻婆豆腐", "番茄炒蛋", "土豆丝",
    "茄子", "西兰花", "小米粥", "葱油拌面", "蒸蛋", "冬瓜汤",
    "排骨汤", "可乐鸡翅", "宫保鸡丁", "鱼香肉丝", "清炒时蔬",
    "红烧肉做法", "番茄牛腩", "酸辣土豆丝", "清蒸鱼", "白灼虾",
    "紫菜蛋花汤", "南瓜粥", "葱油饼", "蒸南瓜", "蒜蓉西兰花",
    "香菇滑鸡", "红烧豆腐", "鸡蛋羹", "肉末茄子", "番茄豆腐汤",
    "蛋炒饭做法", "葱花鸡蛋饼", "萝卜汤", "炖排骨", "炒青菜",
]

# ---------- 心情标签映射 ----------
# 按菜型/关键词打 1-3 个标签
def mood_tags(name: str, desc: str) -> str:
    text = name + " " + desc
    tags = []

    def add(*xs):
        for t in xs:
            if t not in tags and t in VALID_MOODS:
                tags.append(t)

    # 高满足/口腹类
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

    # 思乡菜微调
    if re.search(r"家乡|妈妈|外婆|老家|地道", text):
        add("想家")

    # 最多 3 个
    tags = tags[:3]
    if not tags:
        tags = ["平静", "满足"]
    return ",".join(tags)


def estimate_minutes(name: str, steps: int) -> int:
    text = name
    if re.search(r"粥|汤|炖|煲|红烧|卤|牛腩|排骨", text):
        m = 60 + steps * 5
    elif re.search(r"蒸|清蒸", text):
        m = 25 + steps * 4
    elif re.search(r"凉拌", text):
        m = 10 + steps * 3
    else:
        m = 15 + steps * 3
    # 快手菜上限
    if steps <= 3 and not re.search(r"炖|煲|红烧|粥|汤", text):
        m = min(m, 25)
    return max(5, min(180, int(m)))


def estimate_difficulty(name: str, steps: int) -> str:
    if steps >= 6 or re.search(r"炖|烤|油炸|烘焙|蛋糕", name):
        return "中等"
    return "简单"


# ---------- HTTP 会话 ----------
def new_session() -> requests.Session:
    s = requests.Session()
    s.headers.update({
        "User-Agent": UA,
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Upgrade-Insecure-Requests": "1",
    })
    for attempt in range(3):
        try:
            r = s.get(HOME, timeout=20)
            if r.status_code == 200:
                return s
        except Exception as e:
            print(f"  [home] home fetch err: {e}", flush=True)
        time.sleep(random.uniform(3, 6))
    return s


def polite_sleep(lo=5.0, hi=8.0):
    time.sleep(random.uniform(lo, hi))


def is_blocked(text: str, length: int) -> bool:
    if length < 6000:
        return True
    if "滑动验证" in text or "验证码" in text and len(text) < 8000:
        return True
    return False


def fetch_with_retry(session, url, max_retry=4):
    """返回 text 或 None（彻底失败）"""
    for attempt in range(max_retry):
        polite_sleep()
        try:
            r = session.get(url, headers={"Referer": HOME}, timeout=25)
            text = r.text
            if not is_blocked(text, len(text)):
                return text
            print(f"  [blocked] attempt {attempt+1} len={len(text)} url={url}", flush=True)
        except Exception as e:
            print(f"  [err] {e} url={url}", flush=True)
        # 退避并刷新 cookie
        wait = random.uniform(15, 30)
        print(f"  [backoff] sleep {wait:.1f}s then refresh home", flush=True)
        time.sleep(wait)
        try:
            session.get(HOME, timeout=20)
        except Exception:
            pass
    return None


# ---------- 解析 ----------
IMG_RE = re.compile(r'https://i[12]\.chuimg\.com/[^"\'\s\\]+\.(?:jpg|png)')


def normalize_img(url: str) -> str:
    # 把超大尺寸后缀换成中等，减小加载体积
    return re.sub(r'_\d+w_\d+h\.', '_640w_960h.', url)


def parse_recipe(rid: int, html: str):
    soup = BeautifulSoup(html, "lxml")
    h1 = soup.find("h1")
    if not h1:
        return None
    name = h1.get_text(strip=True)
    if not name:
        return None

    # 封面图
    imgs = list(dict.fromkeys(IMG_RE.findall(html)))
    if not imgs:
        return None
    image = normalize_img(imgs[0])

    # 食材
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
            if unit:
                ings.append(f"{nm} {unit}".strip())
            else:
                ings.append(nm)
    if len(ings) < 3:
        return None

    # 步骤
    steps = [p.get_text(strip=True) for p in soup.select("div.steps li p")]
    steps = [re.sub(r"\s+", " ", s).strip() for s in steps if s.strip()]
    if len(steps) < 3:
        return None

    # 描述：h1 下方导语 / 简短一句
    desc = ""
    intro = soup.select_one("div.desc, div.recipe-show div.ings, p")
    if intro:
        desc = re.sub(r"\s+", " ", intro.get_text(" ", strip=True)).strip()
    if not desc or len(desc) < 8:
        desc = f"{name}，家常做法，步骤清晰易上手。"
    desc = desc[:120]

    minutes = estimate_minutes(name, len(steps))
    difficulty = estimate_difficulty(name, len(steps))
    moods = mood_tags(name, desc)

    return {
        "rid": rid,
        "name": name,
        "description": desc,
        "image": image,
        "ingredients": ings,
        "steps": steps,
        "cooking_time": minutes,
        "difficulty": difficulty,
        "mood_tags": moods,
        "season": "四季",
    }


def collect_search_ids(session, keyword, max_pages=2):
    ids = []
    for page in range(1, max_pages + 1):
        text = fetch_with_retry(session, SEARCH + f"?keyword={requests.utils.quote(keyword)}")
        if text is None:
            break
        found = list(dict.fromkeys(re.findall(r"/recipe/(\d+)/", text)))
        # 过滤明显非菜谱 id（下厨房菜谱 id 通常较长）
        found = [int(x) for x in found if int(x) > 10000]
        ids.extend(found)
        polite_sleep()
    return list(dict.fromkeys(ids))


# ---------- 主流程 ----------
def load_cache():
    if CACHE.exists():
        with open(CACHE, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data  # list of dict
    return []


def save_cache(recipes):
    with open(CACHE, "w", encoding="utf-8") as f:
        json.dump(recipes, f, ensure_ascii=False, indent=2)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", type=int, default=160)
    ap.add_argument("--max-pages", type=int, default=2)
    args = ap.parse_args()

    recipes = load_cache()
    done_ids = {r["rid"] for r in recipes}
    done_names = {r["name"] for r in recipes}
    print(f"[resume] cache has {len(recipes)} recipes", flush=True)

    session = new_session()
    print(f"[home] cookies={list(session.cookies.get_dict().keys())}", flush=True)

    failed_ids = []

    # 先遍历关键词收集 id，再逐个抓详情
    for kw in KEYWORDS:
        if len(recipes) >= args.target:
            break
        print(f"\n=== keyword: {kw} (have {len(recipes)}/{args.target}) ===", flush=True)
        try:
            ids = collect_search_ids(session, kw, max_pages=args.max_pages)
        except Exception as e:
            print(f"  [search err] {e}", flush=True)
            continue
        print(f"  found {len(ids)} ids from search", flush=True)

        for rid in ids:
            if len(recipes) >= args.target:
                break
            if rid in done_ids:
                continue
            url = RECIPE_URL.format(rid=rid)
            html = fetch_with_retry(session, url)
            if html is None:
                failed_ids.append(rid)
                print(f"  [skip] rid={rid} (blocked/failed)", flush=True)
                # 连续被封：长冷却后再继续下一个，避免连坐
                cooldown = random.uniform(45, 75)
                print(f"  [cooldown] {cooldown:.0f}s before next recipe", flush=True)
                time.sleep(cooldown)
                try:
                    session.get(HOME, timeout=20)
                except Exception:
                    pass
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
            print(f"  [ok] {rid} {parsed['name']} ings={len(parsed['ingredients'])} steps={len(parsed['steps'])} moods={parsed['mood_tags']}", flush=True)

    # ---------- 图片 URL 自检（HEAD） ----------
    print("\n=== image HEAD check ===", flush=True)
    bad_imgs = []
    for r in recipes:
        try:
            resp = requests.head(r["image"], timeout=10, allow_redirects=True,
                                 headers={"User-Agent": UA})
            if resp.status_code != 200:
                bad_imgs.append((r["rid"], r["image"], resp.status_code))
        except Exception as e:
            bad_imgs.append((r["rid"], r["image"], str(e)))
    print(f"  bad images: {len(bad_imgs)}", flush=True)
    for b in bad_imgs[:20]:
        print("   ", b, flush=True)

    # 写 PSV
    write_psv(recipes)
    print(f"\n[done] total recipes: {len(recipes)}", flush=True)
    print(f"[done] psv -> {PSV_OUT}", flush=True)
    if failed_ids:
        print(f"[warn] failed ids: {failed_ids}", flush=True)


def write_psv(recipes):
    PSV_OUT.parent.mkdir(parents=True, exist_ok=True)
    header = "# name|moods|minutes|difficulty|description|image|ingredients-json|steps-json\n"
    lines = [header]
    for r in recipes:
        ings_json = json.dumps(r["ingredients"], ensure_ascii=False)
        steps_json = json.dumps(r["steps"], ensure_ascii=False)
        # 安全：字段内不得含 |
        fields = [
            r["name"].replace("|", " "),
            r["mood_tags"],
            str(r["cooking_time"]),
            r["difficulty"],
            r["description"].replace("|", " "),
            r["image"],
            ings_json,
            steps_json,
        ]
        lines.append("|".join(fields) + "\n")
    with open(PSV_OUT, "w", encoding="utf-8", newline="\n") as f:
        f.writelines(lines)


if __name__ == "__main__":
    main()
