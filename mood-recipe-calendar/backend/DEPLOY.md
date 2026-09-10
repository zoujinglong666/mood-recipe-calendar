# 心情菜谱日历 · 后端部署指南

## 1. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 后端运行环境 |
| MySQL | 8.0+ | 数据库，utf8mb4 |
| 服务器 | Linux / Windows | 公网可访问，需 HTTPS 域名 |

## 2. 数据库初始化

```bash
# 登录 MySQL
mysql -uroot -p

# 执行初始化脚本（建库 + 建表 + 24道菜谱 + 周边商品 + 虚拟商品）
source /path/to/backend/sql/init.sql
```

初始化后验证：
```sql
USE mood_recipe;
SHOW TABLES;  -- 应显示 15 张表
SELECT COUNT(*) FROM recipes;  -- 应为 24
```

## 3. 环境变量配置

部署前必须设置以下环境变量（**不要写入代码仓库**）：

```bash
# ===== 必配 =====
export DB_USERNAME=root
export DB_PASSWORD=你的数据库密码
export WECHAT_APPID=wx4da25d0ce1a4938c
export WECHAT_SECRET=你的微信小程序AppSecret
export AGNES_API_KEY=你的Agnes API Key

# ===== 可选（文件上传）=====
export UPLOAD_PUBLIC_BASE_URL=https://你的域名/uploads

# ===== 可选（腾讯云COS，用于图片存储）=====
export TENCENT_COS_ENABLED=false
# export TENCENT_COS_SECRET_ID=xxx
# export TENCENT_COS_SECRET_KEY=xxx
# export TENCENT_COS_REGION=ap-guangzhou
# export TENCENT_COS_BUCKET=mood-recipe-1250000000

# ===== 可选（微信虚拟支付，上线变现时配置）=====
# export WECHAT_VIRTUAL_PAYMENT_OFFER_ID=xxx
# export WECHAT_VIRTUAL_PAYMENT_APP_KEY=xxx
# export WECHAT_SESSION_KEY_ENCRYPTION_KEY=Base64编码的32字节密钥

# ===== 可选（会话有效期）=====
export SESSION_DAYS=30
```

Windows PowerShell 示例：
```powershell
$env:DB_PASSWORD = "你的密码"
$env:WECHAT_APPID = "wx4da25d0ce1a4938c"
$env:WECHAT_SECRET = "你的密钥"
$env:AGNES_API_KEY = "你的Key"
```

## 4. 构建与启动

### 4.1 构建 JAR

```bash
cd backend
./mvnw package -DskipTests
# 产物：target/backend-0.0.1-SNAPSHOT.jar
```

### 4.2 启动

```bash
# 生产环境建议限制内存
java -Xms256m -Xmx512m -jar target/backend-0.0.1-SNAPSHOT.jar
```

### 4.3 后台运行（Linux）

```bash
nohup java -Xms256m -Xmx512m -jar target/backend-0.0.1-SNAPSHOT.jar > backend.log 2>&1 &
```

### 4.4 后台运行（Windows）

```powershell
Start-Process -FilePath "java.exe" `
  -ArgumentList '-Xms256m','-Xmx512m','-jar','target\backend-0.0.1-SNAPSHOT.jar' `
  -WindowStyle Hidden
```

## 5. 健康检查

```bash
curl http://localhost:8080/api/health
# 期望返回：{"status":"UP"}
```

## 6. Nginx 反向代理（HTTPS 必需）

微信小程序要求后端接口必须 HTTPS。配置 Nginx：

```nginx
server {
    listen 443 ssl;
    server_name api.yourdomain.com;

    ssl_certificate     /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    client_max_body_size 20m;  # 支持图片上传

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
    }

    location /uploads/ {
        alias /path/to/backend/uploads/;
        expires 30d;
    }
}
```

## 7. 小程序端配置

修改前端 `.env.production`：
```
VITE_API_BASE_URL=https://api.yourdomain.com/api
```

重新构建：
```bash
cd frontend
pnpm build:mp-weixin
```

在微信开发者工具中导入 `dist/build/mp-weixin`，上传代码。

## 8. 数据库表清单（15张）

| 表名 | 用途 |
|------|------|
| users | 用户表（微信 openid、昵称、头像、会员） |
| user_records | 每日伙食记录 |
| recipes | 菜谱库（24道高质量菜谱） |
| monthly_albums | 月度画册 |
| user_food_preferences | 用户口味偏好 |
| recommendation_exposures | 菜谱推荐曝光记录 |
| recipe_interactions | 菜谱交互行为（点赞/收藏/做过） |
| user_feedback | 用户反馈 |
| operational_events | 运营事件日志 |
| products | 周边商品（锅仔形象馆） |
| checkins | 签到记录 |
| shop_orders | 周边订单 |
| virtual_products | 虚拟商品目录 |
| virtual_orders | 虚拟商品订单 |
| user_entitlements | 用户虚拟权益 |

## 9. 上线检查清单

- [ ] MySQL 已初始化，24道菜谱已入库
- [ ] 环境变量已配置（DB / WECHAT / AGNES）
- [ ] 后端服务启动，健康检查通过
- [ ] HTTPS 域名已配置，Nginx 反向代理正常
- [ ] 小程序 request 合法域名已在微信公众平台配置
- [ ] 小程序 downloadFile 合法域名已配置（菜谱图片使用网络URL：i2.chuimg.com、aka.doubaocdn.com）
- [ ] 前端 `.env.production` API 地址已改为生产域名
- [ ] 前端已重新构建并上传微信开发者工具
- [ ] 微信登录全流程测试通过
- [ ] 图片上传功能测试通过
- [ ] 锅仔菜谱推荐（Agnes AI）测试通过

## 10. 常见问题

**Q: 启动报 "微信登录配置缺失"**
A: 未设置 WECHAT_APPID / WECHAT_SECRET 环境变量。

**Q: 菜谱推荐返回空或报错**
A: 检查 AGNES_API_KEY 是否正确配置，网络是否能访问 apihub.agnes-ai.com。

**Q: 图片上传后无法访问**
A: 配置 UPLOAD_PUBLIC_BASE_URL 为你的域名，或配置 Nginx 的 /uploads/ 路径。

**Q: 小程序请求报 "不在以下 request 合法域名列表中"**
A: 在微信公众平台 → 开发管理 → 开发设置 → 服务器域名中添加你的 HTTPS 域名。

**Q: 菜谱图片不显示**
A: 菜谱图片使用网络URL（下厨房 chuimg.com 和豆包 CDN），需在微信公众平台 → 开发设置 → 服务器域名 → downloadFile 合法域名中添加：`https://i2.chuimg.com` 和 `https://aka.doubaocdn.com`。
