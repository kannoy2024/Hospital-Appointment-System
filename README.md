# 🏥 医院预约挂号系统 · HospitalAppointmentSeries

> 一套「患者小程序 + 医生工作台 + 管理后台」三端贯通的医院预约挂号平台，重点回答一个问题：
> **高并发抢号时，号源怎么才能不被超卖，又不会因为缓存/消息丢了就丢单。**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-42b883.svg)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D.svg)](https://redis.io/)

---

## 📌 项目简介

系统面向「二级医院 / 社区诊所」这类中小型场景，覆盖 **挂号 → 缴费 → 报到 → 就诊 → 开方 → 缴药费 → 取药 → 评价** 的完整门诊闭环。

- **患者端**：原生微信小程序（Vant Weapp），完成选科室 / 选医生 / 抢号 / 支付 / 看报告
- **医生端**：PC 工作台，完成接诊、写病历、开处方
- **管理端**：PC 后台，完成科室 / 医生 / 排班 / 药品 / 用户 / 日志管理

三个端共用一套 REST API，通过 JWT + 角色（`ADMIN` / `DOCTOR` / `USER`）区分权限。

---

## 🎯 三端角色与入口

| 端 | 技术形态 | 目标用户 | 主要能力 |
|---|---|---|---|
| 患者端 | 微信小程序 | 患者 / 家属 | 浏览科室医生、智能导诊、挂号、支付、报到、查病历、家庭就诊人、收藏医生 |
| 医生工作台 | Vue 3 PC 端 | 医生 | 今日排班、候诊队列、接诊、写病历、开处方、发药确认 |
| 管理后台 | Vue 3 PC 端 | 管理员 | 数据看板、排班/号源维护、医生与账号、药品与库存、预约与处方、日志审计 |

---

## ✨ 功能清单

### 患者端（小程序 · 20 个业务页面）

| 模块 | 功能 |
|---|---|
| 首页 / 科室 | 轮播、科室九宫格、医生列表、搜索、按日期筛选排班 |
| 智能导诊 | 症状输入（AI 分析失败自动降级为关键词规则），推荐科室 |
| 挂号 | 选排班 → 选就诊人 → 确认下单 → 支付（余额 / Mock 收银台） |
| 订单 | 待支付 / 待就诊 / 已完成 Tab，缴药费、取消、报到、评价 |
| 就诊人 | 家庭就诊人增删改、设默认、身份证脱敏展示 |
| 我的 | 钱包充值、消息通知、病历记录、处方详情、收藏医生、隐私协议 |

### 医生工作台

排班列表 · 候诊叫号 · 接诊开始 · 病历（主诉/现病史/诊断）录入 · 开处方 · 完成就诊 · 药房发药 · 个人简介维护

### 管理后台（12 个管理模块，菜单动态下发）

数据看板（ECharts）· 科室 · 医生 · 排班 · 预约 · 患者 · 药品（含库存与 Excel 导出）· 用户 · 菜单 · 角色 · 系统日志 · 系统设置

---

## 🛠 技术栈

### 后端

| 技术 | 版本 | 用途 |
|---|---|---|
| Spring Boot | 3.2.5 | 核心框架（Java 21） |
| Spring Security + JJWT | 内置 / 0.12.5 | 无状态 JWT 认证、方法级授权 |
| MyBatis-Plus | 3.5.6 | ORM、逻辑删除、分页、自动填充 |
| Redis (Lettuce) | 7 | 号源缓存、分布式锁、延迟队列 |
| SpringDoc OpenAPI | 2.5.0 | Swagger UI 接口文档 |
| Hutool | 5.8.40 | 工具类（拼音、日期等） |
| FastExcel | 1.2.0 | 排班 / 预约 / 药品明细导出 |
| MySQL Connector/J | 8.x | 数据库驱动 |

### 前端

| 端 | 技术 |
|---|---|
| 管理后台 / 医生端 | Vue 3.4.27 · Vue Router 4.3.2（**动态路由**）· Pinia 2.1.7 · Element Plus 2.7.4 · ECharts 6.1.0 · Vite 5.2.11 · Axios |
| 患者端 | 微信小程序原生框架 · Vant Weapp 1.11.6 · 自定义 TabBar · npm 构建 |

### 部署

Docker Compose（MySQL 8 + Redis 7 + 后端 JRE 21 + Nginx 静态托管）

---

## 📦 项目结构

```
hospital-booking/
├── springboot/                     # 🔧 后端（Spring Boot 3，249 个 Java 文件）
│   └── src/main/java/com/system/
│       ├── controller/             # 30 个 Controller（18 业务 + 12 管理端 controller/admin/）
│       ├── service/ + impl/        # 29 个接口 / 30 个实现
│       ├── entity/                 # 22 个实体
│       ├── mapper/                 # 25 个 MyBatis-Plus Mapper（无 XML，纯注解/Wrapper）
│       ├── dto_vo/dto · vo/        # 26 个入参 DTO / 32 个出参 VO
│       ├── config/                 # 13 个配置类（Security、Redis、MyBatis、OpenAPI、AI、支付…）
│       ├── security/               # 6 个安全组件（JWT 过滤器、Token 提供者、UserDetails…）
│       └── common/                 # 16 个公共组件（Result、异常、AOP 日志、Redis 容错、导出…）
│   └── src/main/resources/
│       ├── application.yml         # 公共配置
│       ├── application-local.yml   # 本地连接信息（🔒 不入库）
│       └── lua/quota_decr.lua      # 号源扣减 Lua 脚本（原子操作）
│
├── miniprogram/                    # 📱 患者端（原生小程序）
│   ├── pages/                      # 20 个业务页面
│   ├── components/                 # 29 个业务组件（doctor-card、time-slot-grid、pay-sheet…）
│   ├── utils/                      # 17 个工具（api / request / adapter / auth / config…）
│   └── custom-tab-bar/             # 自定义底部导航
│
├── vue/                            # 💻 管理端 + 医生工作台（18 个视图 / 16 个 api 模块）
│   ├── src/router/dynamicRoutes.js # 按后端菜单动态注入路由
│   └── src/router/routeMap.js      # 菜单 path → 组件映射表
│
├── sql/                            # 🗄 数据库脚本
│   └── system.sql                  # ⭐ 完整建库脚本（22 张表 + 演示数据，已脱敏）
│
└── docker-compose.yml              # 🐳 四服务一键编排
```

---

## 🧠 核心设计

这一节是项目的技术重点，也是理解整体设计的最佳入口。

### 1. 号源防超卖：四层防线

一次挂号要同时防住「并发超卖」和「缓存/DB 不一致」两类问题，所以做了四层：

| 层 | 手段 | 解决的问题 |
|---|---|---|
| ① Redis 原子扣减 | `lua/quota_decr.lua`：判断剩余 → `DECR` → 若扣成负数立刻 `INCR` 回滚 | 缓存层的并发超卖 |
| ② 分布式锁 | `lock:appointment:{userId}:{scheduleId}` + `SETNX` 5s 过期 | 同一用户对同一排班重复提交（防重复挂号） |
| ③ DB 条件更新 | `UPDATE schedule SET booked_count = booked_count + 1 WHERE id = ? AND booked_count < total_quota` | 最终一致性；缓存没命中/Redis 宕机也不会超卖 |
| ④ 失败补偿 | 任一步失败 → `releaseSlot()` 回滚缓存 + 释放锁（`finally` 中删除） | 避免"扣了名额但下单失败"占坑 |

关键代码：`service/impl/AppointmentServiceImpl.java#create()`、`service/impl/ScheduleCacheServiceImpl.java#tryReserveSlot()`。

### 2. 缓存三大问题（穿透 / 击穿 / 雪崩）

- **穿透**：查不到的排班写入哨兵值 `"NULL"`（TTL 5 分钟），`getRemaining()` 命中即直接返回，不查库
- **击穿**：未命中时用 `lock:quota:{id}` 互斥锁重建，仅一个线程回源
- **雪崩**：预热 TTL 为 8 天 + **0~4 小时随机抖动**，避免同一时刻集体过期

### 3. 超时未支付自动取消：延迟队列 + 定时兜底

- 下单成功 → 写入 Redis ZSet `delay:appointment`（score = 过期时间戳）
- 定时任务每 5 秒（`app.appointment.expire-check-ms`）扫描到期单
- 每个取消动作前**二次校验**仍为 `unpaid` 才执行，避免把已支付订单误取消
- **Redis 不可用时**降级为「数据库每分钟最多扫描一次」，保证功能不丢

### 4. 缓存不可用时的优雅降级

自研 `common/ResilientRedisClient.java`（311 行）作为防腐层：
读失败返回 `null`、写失败只记日志不抛异常、`available` 标记 + 10 秒节流 ping 自愈。
结论：**Redis 挂了系统仍可挂号（走 DB 条件更新），只是丢了缓存加速与延迟队列**。

### 5. 预约 / 处方状态机

预约单（`appointment.status`）的合法迁移：

| 当前状态 | 触发动作 | 下一状态 | 备注 |
|---|---|---|---|
| `unpaid` | 支付成功 | `pending` | `AppointmentPayHandler` |
| `unpaid` | 超时 / 手动取消 | `cancelled` | 释放号源 |
| `pending`（兼容旧值 `confirmed`） | 报到 | `checked_in` | 可报到窗口 = 时段结束前 60 分钟起 |
| `pending` | 取消 | `cancelled` | 已支付则先退款，再释放号源 |
| `checked_in` | 医生开始接诊 | `in_consultation` | |
| `in_consultation` | 完成就诊（未开方） | `completed` | |
| `in_consultation` | 完成就诊（已开方） | `pending_rx_pay` | 等患者缴药费 |
| `pending_rx_pay` | 药房发药 `dispense` | `completed` | 缴药费**不改**预约状态，发药才结束 |

处方单（`prescription.status`）：`unpaid → paid → dispensed`，任意阶段可 `cancelled`，药费支付有效期 24 小时。

> 易错点：前端「待取药」是**伪状态** `pending_dispense`，落库值仍是 `pending_rx_pay`，
> 由 `miniprogram/utils/adapter.js#resolveDisplayStatus()` 结合处方状态换算得出。

### 6. 动态菜单 + 角色可见性（RBAC）

菜单与角色的关系存放在中间表 `sys_role_menu(role_code, menu_id)`，一个菜单可被多个角色同时看到。
登录后 `GET /api/admin/menus` 按当前用户角色返回可见菜单 → `setupDynamicRoutes()` 用 `routeMap.js`
把 `path` 映射到组件并 `addRoute`。新增管理页只需插一条 `sys_menu` + 在 `routeMap` 里登记一次，不用改路由文件。

> ⚠️ **菜单可见性 ≠ 接口权限**：隐藏菜单只是不让它出现在侧栏，真正的访问边界在各 Controller 的
> `@PreAuthorize` 上。给某个页面开放新角色时，必须同时调整对应接口的授权注解，否则会出现「看得见、点进去 403」。

### 7. 其他工程化细节

- **排班自动补齐**：启动时 `ScheduleBootstrapRunner` 为在岗医生生成未来 7 天 × 6 时段 × 默认 5 号
- **支付通道可切换**：`wechat.pay.channel: mock | wechat`，当前为 `mock`，本地无需商户号即可走完整支付/退款链路
- **审计日志**：登录日志 + `@Log` 注解 + AOP 切面写入 `sys_operation_log`
- **统一响应**：`Result{code,message,data}` + `GlobalExceptionHandler`，业务异常一律 `BusinessException`

---

## 🚀 快速开始

### 0. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21+ | 后端编译运行 |
| Maven | 3.8+ | 或直接用 IDEA 内置 |
| Node.js | 18+（推荐 20/22） | 管理后台构建 |
| MySQL | 8.0+ | 8.4 实测通过 |
| Redis | 7.x | 无密码或配置密码均可 |
| 微信开发者工具 | 最新 | 仅患者端需要 |

### 1. 初始化数据库

```bash
# 脚本自带 CREATE DATABASE / USE，直接导入即可（会先 DROP 同名表）
mysql -u root -p < sql/system.sql
```

> 脚本内含**已脱敏的演示数据**：患者姓名 / 手机号 / 身份证 / 邮箱 / 微信 openid 均为虚构值，
> 可直接用于本地开发和演示。

### 2. 创建本地配置文件（必做）

`application.yml` 里的数据源与 Redis 连接信息全部来自 `${local.*}` 占位符。
该文件已被 `.gitignore` 忽略，**新克隆的仓库里没有它，需要自己创建**；缺失时应用仍能启动，
但连不上数据库和 Redis。在 `springboot/src/main/resources/` 下新建：

```yaml
local:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    host: localhost
    port: 3306
    database: hospital_booking
    username: hospital          # 建议单独建库用户，别用 root
    password: 你的数据库密码
  redis:
    host: localhost
    port: 6379
    database: 0
    password: ""                # Redis 无密码就留空
    timeout: 3000ms
    pool:
      max-active: 16
      max-idle: 8
      min-idle: 4
      max-wait: 2000ms

# 可选：小程序微信登录需要（与 project.config.json 的 appid 配对）
wechat:
  miniapp:
    app-id: wx08abffa32f3a8e19
    app-secret: 你的AppSecret

# 可选：AI 导诊，不开也能用（自动降级关键词规则）
ai:
  enabled: false
```

> 🔒 该文件已被 `.gitignore` 忽略，不要提交。

### 3. 启动后端

```bash
cd springboot
mvn spring-boot:run
# 或：mvn clean package -DskipTests && java -jar target/system-server-1.0.0.jar
```

- 服务地址：http://localhost:9090
- 接口文档：http://localhost:9090/swagger-ui.html

### 4. 启动管理后台（含医生工作台）

```bash
cd vue
npm install
npm run dev
```

- 访问：http://localhost:5173 （已配 `/api`、`/uploads` 代理到 9090）

### 5. 运行患者端小程序

```bash
cd miniprogram
npm install
npm run build:npm        # 构建 Vant Weapp 到 miniprogram_npm/
```

1. 微信开发者工具 → 导入 `miniprogram/` 目录
2. 详情 → 本地设置 → 勾选 **「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」**
3. 真机调试时把 `miniprogram/utils/config.js` 的 `baseUrl` 改成电脑局域网 IP（如 `http://192.168.1.100:9090`）

### 6. Docker 一键部署（可选）

在仓库根目录新建 `.env`（已被 gitignore，不会入库），至少包含：

```bash
MYSQL_ROOT_PASSWORD=改成强密码
MYSQL_PASSWORD=改成强密码
REDIS_PASSWORD=改成强密码
JWT_SECRET=至少32位随机字符串
WX_APP_SECRET=小程序 AppSecret（不用微信登录可留空）
AI_API_KEY=DeepSeek Key（不用 AI 导诊可留空）
```

```bash
docker compose up -d --build
```

- 前台入口：http://localhost（Nginx 托管 Vue 并反代 `/api`）
- 后端 9090 默认不对宿主机暴露（走 Nginx），本地调试需放开 `docker-compose.yml` 里注释的 `ports`
- MySQL 容器首次启动会自动导入 `sql/system.sql`

---

## 🔑 默认账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin` |
| 医生 | `doctorzmh` / `doctorlxf` / `doctorzdh` …（见 `sys_user` 表） | `123`（`app.user.default-password`） |
| 患者 | 小程序注册 / 微信登录，或 `POST /api/auth/register` | 注册时自定义（6~50 位） |

> 医生账号由管理端创建时自动生成（见 `DoctorAccountServiceImpl`），初始密码统一为 `123`。
> 忘记管理员密码时，可把 `sys_user` 里 `admin` 的 `password` 直接替换为已知哈希（BCrypt，`123456` 对应
> `$2a$10$pi40DlZhvbwtX5BTzQnlXOCXBTJI25PQP75beTjkqiLjhmoUWsihS`），登录后再改回来。

---

## 🌐 接口一览

所有接口以 `/api` 为前缀，除标注外都需要 `Authorization: Bearer <token>`。

| 模块 | 路径前缀 | 说明 |
|---|---|---|
| 认证 | `/api/auth` | `login` / `register` / `wx-login` / `me`（前三个开放） |
| 科室 | `/api/departments` | 列表、详情（GET 开放） |
| 医生 | `/api/doctors` | 列表、详情（GET 开放） |
| 排班 | `/api/schedules` | 按科室/医生/日期查询（GET 开放） |
| 预约 | `/api/appointments` | 下单、支付、取消、报到、评价、病历、处方 |
| 收藏 | `/api/favorites` | 列表、收藏、取消、检查（`USER` 角色） |
| 就诊人 | `/api/patients` | 我的就诊人 CRUD、设默认 |
| 处方 | `/api/prescriptions` | 详情、缴药费、预支付 |
| 钱包 | `/api/wallet` | 充值、流水 |
| 支付 | `/api/payments`、`/api/pay/notify`、`/mock/pay` | 支付单查询、回调、Mock 收银台 |
| 通知 | `/api/notifications` | 列表、未读数、已读 |
| 导诊 | `/api/triage/analyze` | 症状 → 科室（开放） |
| 文件 | `/api/files` | 上传、下载、预览、删除 |
| 个人 | `/api/profile` | 资料、头像、改密码 |
| 医生工作台 | `/api/doctor` | 排班、候诊、接诊、病历、开方、发药 |
| 管理端 | `/api/admin/**` | 12 个模块，需 `ADMIN` 角色 |

完整参数与示例见 Swagger UI（`/swagger-ui.html`）。

---

## ⚙️ 关键配置

| 配置项 | 默认值 | 含义 |
|---|---|---|
| `app.appointment.unpaid-expire-minutes` | 30 | 挂号未支付超时（分钟） |
| `app.appointment.expire-check-ms` | 5000 | 超时扫描间隔（毫秒） |
| `app.appointment.default-fee-fen` | 2000 | 挂号费（分），**服务端定价**，不信任前端传值 |
| `app.prescription.pay-expire-minutes` | 1440 | 药费支付有效期（24 小时） |
| `app.recharge.max-fen` | 1000000 | 单次充值上限（1 万元） |
| `app.schedule.cache-warmup` | true | 启动时预热号源到 Redis |
| `app.bootstrap.schedule` | true | 启动时自动补齐未来 7 天排班 |
| `app.export.max-rows` | 5000 | 导出单次上限 |
| `ai.enabled` | false | AI 导诊开关（关闭时走关键词降级） |
| `wechat.pay.channel` | mock | 支付通道：`mock` / `wechat` |
| `jwt.expiration` | 86400000 | Token 有效期（24 小时） |

金额统一用 **`long` 存「分」**，避免浮点误差；展示层再 /100 格式化。

---

## ⚠️ 已知问题与技术债

诚实记录当前未完成 / 待改进的点，方便后续迭代：

1. **`sys_menu.roles` 是历史列**：角色-菜单关系已迁移到 `sys_role_menu`，旧列仅作回滚保险保留，后续版本会移除；
   当前新建菜单时该列由数据库默认值填充，与实际角色可能不一致，请以 `roleCodes` 为准。
2. **`updateMenu` 清空图标不生效**：MyBatis-Plus 的 `updateById` 默认忽略 null 字段，把图标清空后保存不会覆盖旧值。
3. **「待取药」Tab 的分页总数不准**：`AppointmentServiceImpl#pageMine` 在内存里二次过滤后把当前页条数当作 `total`，翻页时会偏小。
4. **支付为 mock 通道**：`wechat.pay.channel: mock`，真实微信支付尚未接入，本地无需商户号即可跑通支付/退款链路。
5. **没有自动化测试**：`springboot/src/test` 未纳入仓库，重构时依赖人工回归。

---

## 📚 文档与备注

- 接口以 Swagger 为准，README 只做模块级索引。
- 前端路由由后端菜单驱动，新增页面记得同步 `sys_menu` 与 `vue/src/router/routeMap.js`。
