# 🍲 心情菜谱日历（Mood Recipe Calendar）

> 一款通过记录每日饮食，自动生成「心情菜谱日历」和「年度干饭报告」的治愈系生活工具。
> 本项目为**前后端一体**仓库：前端 uni-app (Vue3 + Vite) + Wot Design Uni v2，后端 Spring Boot 4（Java 21），数据库 **MySQL 8**。

## 目录结构

```
mood-recipe-calendar/
├── frontend/                      # 前端：uni-app (Vue3 + Vite) + Wot Design Uni v2
│   ├── src/
│   │   ├── pages/                 # 业务页面（8 个，uni-pages 自动注册）
│   │   │   ├── index/             #   首页
│   │   │   ├── mood/              #   心情选择
│   │   │   ├── recipe/            #   推荐结果
│   │   │   ├── record/            #   每日记录
│   │   │   ├── calendar/          #   月视图日历
│   │   │   ├── album/             #   月度画册
│   │   │   ├── report/            #   年度报告（8 页横滑）
│   │   │   └── profile/           #   个人主页
│   │   ├── components/            # 公共组件（自动注册，模板可直接使用）
│   │   │   ├── common/AppNav.vue  #   导航栏（支持 overlay 浮层模式）
│   │   │   ├── common/DesignSheet.vue # 设计稿整页展示（视觉主体）
│   │   │   ├── common/StatRow.vue #   三列统计条
│   │   │   ├── guozai/Guozai.vue  #   品牌 IP「锅仔」纯 CSS 拟人化，8 种心情
│   │   │   ├── mood/MoodGrid.vue  #   8 心情网格选择
│   │   │   └── recipe/RecipeCard.vue # 菜品卡片
│   │   ├── static/design/         # 20 张已验收设计稿 PNG（页面视觉主体）
│   │   ├── api/record.ts          # 后端 API 封装（保存/拉取记录 → MySQL）
│   │   ├── styles/theme.scss      # 公共样式主题（PRD 配色变量 + mrc- 工具类）
│   │   ├── layouts/               # 页面布局（default / tabbar）
│   │   ├── router/  store/  composables/   # 模板自带
│   │   └── pages.json             # 页面路由（uni-pages 插件托管）
│   ├── .codex/config.toml         # wot-cli 接入：Codex MCP 配置
│   ├── .agents/skills/            # wot-ui-v2 Agent Skill
│   ├── AGENTS.md
│   └── package.json
└── backend/                       # 后端：Spring Boot 4.1.1 (Java 21)
    ├── pom.xml                    # 已含 data-jpa + mysql-connector-j
    ├── mvnw / mvnw.cmd
    ├── sql/init.sql               # MySQL 建库建表脚本
    └── src/main/java/com/moodrecipe/backend/
        ├── config/CorsConfig.java # 全局跨域（/api/** 放行）
        ├── controller/
        │   ├── HealthController.java   # GET /api/health
        │   ├── RecipeController.java   # GET /api/recipes/today
        │   └── RecordController.java   # 记录 增/查/删/统计
        ├── entity/UserRecord.java      # JPA 实体
        ├── repository/UserRecordRepository.java
        └── model/  (Recipe / RecordRequest)
```

## 环境要求

| 组件    | 版本 | 说明 |
| ------- | ---- | ---- |
| Node.js | ≥ 20.19.0 / 22 | 前端运行时（本机 v22） |
| pnpm    | 9.9.0 | 前端包管理器（模板锁定） |
| JDK     | 21 | 后端运行时 |
| Maven   | 无需安装 | 使用 `mvnw` Wrapper |
| MySQL   | 8.0 | 本机已有服务 `MySQL80` |

> ⚠️ 本机系统变量 `JAVA_HOME` 指向 JDK 8，Spring Boot 4 需 JDK 17+。启动后端前需在**命令会话内**覆盖 `JAVA_HOME`（见下）。

## 快速开始

### 1. 初始化 MySQL

本机已安装 MySQL 8.0（服务 `MySQL80` 运行中）。执行：

```
mysql -uroot -p < backend/sql/init.sql
```

然后编辑 `backend/src/main/resources/application.properties`，把 `spring.datasource.password` 改为你本机 MySQL 密码。

### 2. 后端（Spring Boot + MySQL）

```
cd backend
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"   # 覆盖到 JDK21
.\mvnw.cmd spring-boot:run
```

后端默认运行在 http://localhost:8080 ，启动时 JPA 自动建表（ddl-auto=update）。

### 启用 AI 菜谱推荐

推荐接口会优先调用 **OpenAI 兼容的 Chat Completions API**，生成包含食材、步骤、时长和难度的完整菜谱；未配置密钥或模型调用失败时，会自动回退到本地菜谱库。

在启动后端的同一个 PowerShell 会话中设置：

```powershell
$env:AI_RECIPE_API_KEY = "你的密钥"
$env:AI_RECIPE_MODEL = "你的模型名"
# 可选：接入其他兼容服务时覆盖默认地址
$env:AI_RECIPE_BASE_URL = "https://你的服务地址/v1/chat/completions"
```

密钥不会保存在 `application.properties` 或提交到仓库。

### 3. 前端（H5 开发模式）

```
cd frontend
pnpm install --ignore-scripts        # 首次：Windows 下 pnpm postinstall 有已知崩溃
node node_modules/@uni-helper/unocss-preset-uni/scripts/apply-patch.mjs
pnpm dev                             # http://localhost:5173/（被占用则 5174）
```

其他平台：`pnpm dev:mp-weixin`（微信小程序）、`pnpm dev:app`（App）、`pnpm build`（H5 打包）。

## 后端接口

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| GET  | `/api/health` | 健康检查 |
| GET  | `/api/recipes/recommend?mood=疲惫` | AI 优先生成今日菜谱，失败时回退菜谱库 |
| POST | `/api/recipes/deep-recommend` | 消耗已购 AI 次数权益，按食材/时长/口味深度生成 |
| GET  | `/api/virtual-commerce/products` | 可售虚拟商品 SKU 列表 |
| POST | `/api/virtual-commerce/orders` | 创建虚拟商品待支付订单（只接受 `openid` 和 `sku`） |
| POST | `/api/virtual-commerce/orders/{orderNo}/payment-params` | 服务端生成 `wx.requestVirtualPayment` 所需的 `signData`、`paySig`、`signature` |
| GET  | `/api/virtual-commerce/entitlements?openid=` | 当前有效数字权益 |
| POST | `/api/records` | 保存每日记录（JSON 落 MySQL） |
| GET  | `/api/records?openid=demo-user` | 用户全部记录（倒序） |
| GET  | `/api/records/month?openid=&month=2026-09` | 某月记录 |
| DELETE | `/api/records/{id}` | 删除记录 |
| GET  | `/api/records/stats?openid=demo-user` | 统计（总条数/天数/心情分布） |

前端已封装 `src/api/record.ts`：记录发布时**本地存储兜底 + 同步写入 MySQL**（后端未启动时静默降级，前端演示不断）。

### 虚拟支付接入状态

仓库已包含虚拟商品目录、待支付订单和权益发放模型，初始 SKU 为 `AI_MENU_7D`、`ALBUM_HD_EXPORT`、`GUOZAI_MEMBER_30D`。订单只有在微信虚拟支付服务端回调完成**签名验签和商品/金额核验**后，才允许调用 `VirtualCommerceService#fulfillPaidOrder` 发货；客户端没有可伪造的“支付成功”接口。

### 个人小程序虚拟支付上线清单

1. 在小程序后台开通虚拟支付、创建三个“道具直购”商品，并将每个后台道具 ID 写入 `virtual_products.platform_item_id`；金额必须与 `price_fen` 一致。
2. 只在部署环境设置 `WECHAT_VIRTUAL_PAYMENT_OFFER_ID`、`WECHAT_VIRTUAL_PAYMENT_APP_KEY`、`WECHAT_SESSION_KEY_ENCRYPTION_KEY`（Base64 编码的 32 字节随机值）和 `WECHAT_MESSAGE_PUSH_TOKEN`。这些值均不可写入前端或提交到仓库。
3. 小程序端顺序为：创建业务订单 → 请求 `/payment-params` → 调用 `uni.requestVirtualPayment`。支付弹窗的成功回调只能刷新订单状态，**不能**直接发放权益。
4. 在小程序后台“消息推送”配置 `GET/POST https://你的域名/api/wechat/virtual-payment/callback` 和相同 Token。当前适配器接收通过 Token 校验的明文 `xpay_goods_deliver_notify`，并以 `OutTradeNo`、`ProductId`、实际金额、`OpenId` 四重核验后幂等发货；若后台启用安全模式加密消息，需要先补充 EncodingAESKey 解密适配后再切换。

接入真实支付前需完成：在小程序管理后台开通虚拟支付、逐个录入 SKU 对应道具、配置回调地址/证书/密钥，并实现微信支付参数调起与验签适配器。实物周边继续沿用 `products` / `shop_orders`，不要接入这一套虚拟商品订单。

## wot-cli（Wot Design Uni CLI）接入

前端已接入 [@wot-ui/cli](https://cli.wot-ui.cn/#install)，为 AI 编程工具提供 wot-ui 组件知识层（真实 props/events/slots，离线可用）。

```
wot info Button           # 查询组件 API
wot demo Button           # 查看组件示例
wot list                  # 组件列表
```

## 页面实现进度（对照设计稿）

> **视觉架构**：为保证与高保真设计稿高度一致，每个页面以对应设计稿 PNG（`src/static/design/`）为**视觉主体整图展示**，交互通过底部悬浮操作条（`dbar`）与透明浮层导航（`AppNav overlay`）提供。画册/年报为多张设计稿竖向连排。

| 页面 | 设计稿 | 交互 |
| ---- | ---- | ---- |
| 首页 | 01 | 心情选菜 / 今日幸运菜 / 日历 / 我的 + 记录悬浮入口 |
| 心情选择 | 02 | 8 心情快捷入口 + 随机推荐 |
| 推荐结果 | 03 | 我做了这道菜 / 换一道 |
| 每日记录 | 04 | 手动录入弹层（真实落库）+ 示例记录 |
| 月视图日历 | 05 | 生成月度画册 / 记录伙食 |
| 月度画册 | 06-10 | 5 页竖向连排 + 分享 |
| 年度报告 | 12-19 | 8 页竖向连排 + 分享 |
| 个人主页 | 11 | 我的年度报告 / 返回首页 |

公共样式统一收敛于 `src/styles/theme.scss`；设计稿整页模式工具类（`.dpage` / `.dbar` / `.dbar__btn`）亦在其中。

## 已交付设计稿索引

- 全部设计稿预览：`../../全部设计稿/设计稿预览.html`
- IP 视觉资产：`../../IP视觉资产/IP视觉资产预览.html`
- 表情包：`../../IP视觉资产/表情包/`（静态 27 张 + 微信上架包 + 动态 GIF 专属动画）

## 下一步规划

- [ ] 微信登录换取真实 openid，替换 `DEMO_OPENID`
- [ ] 菜谱库接入 `recipes` 表，`/api/recipes/today` 按心情查库
- [ ] 月度画册/年度报告的 AI 寄语接大模型 API
- [ ] 图片上传到本地/云存储（当前 record 存的是本地临时路径）
- [ ] 分享长图 / 广告 / 会员等变现场景
