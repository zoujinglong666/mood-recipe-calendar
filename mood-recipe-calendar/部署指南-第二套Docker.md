# 第二套部署指南（Docker，与第一套同服务器）

> 适用场景：服务器上已部署第一套（另一个小程序，占 8080 端口，Docker 部署），现在把 **mood-recipe-calendar** 作为第二套部署上去。
>
> 第二套完全自包含（backend + nginx 两个容器），宿主机只占 **8123** 端口，不与第一套冲突。

## 一、架构总览

```
用户（小程序/H5）
    │
    ▼
域名 https://moodrecipe.icu
    │
    ▼
外部 nginx / 第一套 nginx（HTTPS 终止）
    │ 反代 / → 127.0.0.1:8123
    ▼
mood-recipe-nginx 容器（:80 → 宿主机 8123）
    ├── /      → 前端 H5 静态文件（frontend/dist/build/h5）
    └── /api/  → 反代到 mood-recipe-backend:8080
                        │
                        ▼
                 mood-recipe-backend 容器
                 （Spring Boot，连 MySQL mood_recipe 库，传 COS）
```

## 二、与第一套的资源隔离

| 资源 | 第一套 | 第二套 |
|---|---|---|
| 后端宿主机端口 | 8080 | **8123** |
| MySQL 库 | 第一套库名 | **mood_recipe**（独立新库） |
| 容器名 | 第一套容器 | `mood-recipe-backend` / `mood-recipe-nginx` |
| COS 前缀 | 第一套前缀 | **mood-recipe/** |
| 微信 appid | 第一套 appid | **wx4da25d0ce1a4938c**（或你的新 appid） |
| 域名 | 第一套域名 | **moodrecipe.icu** |

## 三、前置准备

### 1. 域名
- 域名 `moodrecipe.icu` 已购买，A 记录解析到服务器 IP
- 小程序后台 → 开发管理 → 开发设置 → 服务器域名，把 `https://moodrecipe.icu` 加入 request 合法域名
- HTTPS 证书：用第一套的通配符证书，或用 certbot 单独签

### 2. 微信小程序
- 确认 appid（当前 manifest.config.ts 里是 `wx4da25d0ce1a4938c`）
- 如果是新小程序，改 `frontend/manifest.config.ts` 的 mp-weixin.appid，并在 `.env` 里填新的 WECHAT_APPID/SECRET
- 虚拟支付（如用到）：小程序后台开通，创建道具，把 OfferID/AppKey 填入 `.env`

### 3. MySQL
- 第二套用独立库 `mood_recipe`（第一套不是这个库名，不冲突）
- 确认 MySQL 能从 Docker 容器访问到：
  - **MySQL 在宿主机**：用 `host.docker.internal`（docker-compose.yml 已配 extra_hosts，Linux 兼容）
  - **MySQL 在第一套 Docker 容器里**：需要把第二套加入第一套的 docker 网络（见下文第六节）

## 四、本地构建（在你电脑上执行）

```bash
cd mood-recipe-calendar

# 1. 后端打包
cd backend
mvn package -DskipTests
# 产物：backend/target/backend-0.0.1-SNAPSHOT.jar

# 2. 前端 H5 打包（.env.production 里 VITE_API_BASE_URL 已设为 https://moodrecipe.icu/api）
cd ../frontend
pnpm install --ignore-scripts
node node_modules/@uni-helper/unocss-preset-uni/scripts/apply-patch.mjs
pnpm build:h5
# 产物：frontend/dist/build/h5/

# 3. 微信小程序打包（如需发布小程序）
pnpm build:mp-weixin
# 产物：frontend/dist/build/mp-weixin/，用微信开发者工具上传
```

## 五、服务器部署

### 1. 上传文件到服务器

把以下文件/目录上传到服务器（如 `/opt/mood-recipe-calendar/`）：

```
mood-recipe-calendar/
├── backend/target/backend-0.0.1-SNAPSHOT.jar
├── backend/Dockerfile
├── frontend/dist/build/h5/
├── docker/nginx/default.conf
├── docker-compose.yml
├── .env.example
└── backend/sql/init.sql
```

### 2. 建数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS mood_recipe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p mood_recipe < backend/sql/init.sql
# 可选：导入高质量菜谱库
mysql -uroot -p mood_recipe < backend/sql/recipes_high_quality.sql
```

### 3. 配置环境变量

```bash
cp .env.example .env
vim .env   # 填入真实密钥：DB_PASSWORD、COS 密钥、微信 appid/secret 等
```

关键项说明：
- `DB_HOST`：MySQL 在宿主机填 `host.docker.internal`；在第一套容器里填第一套 MySQL 容器名
- `DB_PASSWORD`：你的 MySQL root 密码
- `TENCENT_COS_SECRET_ID/KEY`：COS 密钥
- `WECHAT_APPID/SECRET`：小程序凭证

### 4. 启动容器

```bash
cd /opt/mood-recipe-calendar
docker compose up -d --build

# 查看状态
docker compose ps
docker compose logs -f backend
```

### 5. 验证后端

```bash
curl http://127.0.0.1:8123/api/health
# 期望：{"status":"UP",...}
```

## 六、外部 nginx 反代（moodrecipe.icu + HTTPS）

在第一套的 nginx（或宿主机 nginx）里加一个 server block：

```nginx
server {
    listen 443 ssl http2;
    server_name moodrecipe.icu;

    # SSL 证书（用第一套的通配符证书或单独签）
    ssl_certificate     /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;

    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8123;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# HTTP 强制跳 HTTPS
server {
    listen 80;
    server_name moodrecipe.icu;
    return 301 https://$host$request_uri;
}
```

重载 nginx：`nginx -t && nginx -s reload`

## 七、如果 MySQL 在第一套 Docker 容器里

第二套默认网络连不到第一套的 MySQL 容器，需要加入第一套网络：

1. 查看第一套网络名：`docker network ls`
2. 编辑 `docker-compose.yml`，取消底部 networks 注释，填入第一套网络名
3. `.env` 里把 `DB_HOST` 改为第一套 MySQL 容器名（如 `first-mysql`）
4. 重启：`docker compose up -d`

## 八、验证清单

- [ ] `curl http://127.0.0.1:8123/api/health` 返回 UP
- [ ] `curl https://moodrecipe.icu/api/health` 返回 UP（HTTPS 通）
- [ ] 浏览器打开 `https://moodrecipe.icu` 能看到首页
- [ ] 小程序开发者工具里 request 域名校验通过，能登录
- [ ] 上传一张菜品图片，返回 URL 是 `https://static.image-zero.art/mood-recipe/uploads/...`，浏览器能打开
- [ ] 上传头像，返回 URL 是 `https://static.image-zero.art/mood-recipe/avatar/...`
- [ ] 保存一条记录，MySQL `mood_recipe` 库里 `user_records` 表有数据

## 九、常见问题

**Q: 后端启动报 `Communications link failure` / 连不上 MySQL？**
A: 检查 `.env` 的 `DB_HOST`。宿主机 MySQL 用 `host.docker.internal`；第一套容器 MySQL 用容器名并加入第一套网络。用 `docker exec -it mood-recipe-backend wget http://host.docker.internal:3306` 测试连通性。

**Q: 8123 端口被占了？**
A: `netstat -tlnp | grep 8123` 查看，或改 `docker-compose.yml` 里 nginx 的 ports 映射为 `8082:80`。

**Q: 上传图片返回 COS 配置不完整？**
A: 检查 `.env` 里 `TENCENT_COS_SECRET_ID/KEY` 是否填了，`docker compose up -d` 重启让 env 生效。

**Q: 小程序登录失败？**
A: 检查 `.env` 的 `WECHAT_APPID/SECRET` 与 `manifest.config.ts` 的 appid 一致；小程序后台 request 域名已加白名单。

**Q: 怎么更新代码？**
A: 本地重新 `mvn package` + `pnpm build:h5`，上传覆盖，然后 `docker compose up -d --build`（后端变了）或 `docker compose restart nginx`（只前端变了）。
