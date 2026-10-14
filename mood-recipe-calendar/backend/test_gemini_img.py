import requests, json, base64

API_KEY = "AIzaSyCXH7W4moHdCINx7IkN94tkqTR8AmdIa-4"
URL = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key={API_KEY}"

# 测试生成番茄炒蛋
payload = {
    "contents": [{
        "parts": [{
            "text": "A realistic food photograph of Chinese tomato scrambled eggs (番茄炒蛋), served on a white ceramic plate, bright red tomato chunks with fluffy golden eggs, garnished with chopped green scallions, natural daylight, top-down angle, food photography style, high resolution, appetizing"
        }]
    }],
    "generationConfig": {
        "responseModalities": ["IMAGE"]
    }
}

print("正在生成番茄炒蛋图片...")
r = requests.post(URL, json=payload, timeout=60)
print(f"状态码: {r.status_code}")

if r.status_code == 200:
    data = r.json()
    # 提取图片
    for part in data['candidates'][0]['content']['parts']:
        if 'inlineData' in part:
            img_data = base64.b64decode(part['inlineData']['data'])
            with open("test_tomato_egg.png", "wb") as f:
                f.write(img_data)
            print(f"图片已保存: test_tomato_egg.png ({len(img_data)} bytes)")
            break
else:
    print(f"错误: {r.text[:500]}")
