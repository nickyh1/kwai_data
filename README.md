# kwai_data — 快手店铺数据同步服务

定时从快手开放平台拉取多店铺的订单、未结算账单、提现记录和卖家信息，写入本地 MySQL，供报表查询导出。

---

## 目录

- [架构概览](#架构概览)
- [快速启动](#快速启动)
- [配置说明](#配置说明)
- [数据库初始化](#数据库初始化)
- [同步逻辑](#同步逻辑)
- [项目结构](#项目结构)

---

## 架构概览

```
快手开放平台 API
      │
      ▼
Spring Boot 定时任务（每 3 小时）
      │  多店铺并行（CompletableFuture + FixedThreadPool）
      ▼
┌─────────────────────────────────────┐
│  KwaiFacade.syncAll()               │
│  ├── syncSellerInfo      卖家信息   │
│  ├── WRecordupsert       提现记录   │
│  ├── syncUnsettled       未结算订单 │
│  └── syncOrders          普通订单   │
└─────────────────────────────────────┘
      │  JdbcTemplate.batchUpdate()
      │  INSERT ... ON DUPLICATE KEY UPDATE
      ▼
    MySQL 8.0
```

---

## 快速启动

### 方式一：Docker Compose（推荐）

**前置条件：** Docker Desktop 或 Docker Engine + Compose 插件

```bash
# 1. 克隆项目
git clone https://github.com/nickyh1/kwai_data.git
cd kwai_data

# 2. 创建 .env 文件（放敏感配置，已在 .gitignore 中忽略）
cp .env.example .env
# 编辑 .env，填入真实的密码和 API 密钥

# 3. 创建外部配置目录，放含店铺 token 的配置文件
mkdir -p config
cp src/main/resources/application.yml config/application.yml
# 编辑 config/application.yml，填入 kwai.shops 下各店铺的 token

# 4. 启动
docker compose up -d

# 查看日志
docker compose logs -f app
```

MySQL 和应用会同时启动，**Flyway 在应用启动时自动建表**，无需任何手动操作。

---

### 方式二：本地开发（不用 Docker）

**前置条件：** JDK 17、Maven 3.8+、MySQL 8.0

```bash
# 1. 本地安装快手 SDK（Maven 不在中央仓库）
mvn install:install-file \
  -Dfile=lib/kuaishou-merchant-open-sdk-release_open_kwaishop_sdk-1.0.7633.jar \
  -DgroupId=com.kuaishou -DartifactId=merchant-open-sdk \
  -Dversion=1.0.7633 -Dpackaging=jar

# 2. 在 MySQL 中手动建库（表由 Flyway 自动创建）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ERP CHARACTER SET utf8mb4;"

# 3. 修改 src/main/resources/application.yml
#    填入 spring.datasource.* 连接信息和 kwai.shops token

# 4. 编译并启动
mvn spring-boot:run
```

---

## 配置说明

### `.env` 文件（Docker 部署时使用）

```dotenv
# MySQL
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_PASSWORD=your_kwai_db_password

# 快手 API
KWAI_APP_KEY=ks670191920826361315
KWAI_APP_SECRET=your_app_secret
KWAI_SIGN_SECRET=your_sign_secret
```

`.env.example` 已提供模板，复制后填入真实值即可。

---

### `config/application.yml`（店铺 Token 配置）

Token 较长，推荐挂载外部配置文件而非写入环境变量：

```yaml
kwai:
  app-key: "ks670191920826361315"
  app-secret: "your_app_secret"
  sign-secret: "your_sign_secret"
  shops:
    shop-a:                          # 店铺标识，自定义，对应数据库 shop_key 列
      access-token: "ChF..."
      refresh-token: "ChJ..."
    shop-b:
      access-token: "ChF..."
      refresh-token: "ChJ..."
```

- 每个 `shops.*` 键就是 `shop_key`，会出现在所有数据表的 `shop_key` 列
- 添加新店铺只需在此处新增一项，无需改代码

---

## 数据库初始化

**由 Flyway 全自动管理，无需手动执行任何 SQL。**

| 场景 | 行为 |
|---|---|
| 全新数据库 | 应用启动时 Flyway 自动执行 `V1__init_schema.sql`，创建所有表和索引 |
| 已有数据的库（老版本迁移） | `baseline-on-migrate: true` 自动跳过已存在的表，不影响现有数据 |
| 后续结构变更 | 新增 `V2__xxx.sql`、`V3__xxx.sql` 文件，下次启动自动执行 |

迁移脚本位置：`src/main/resources/db/migration/`

---

### 数据库表结构

| 表名 | 说明 |
|---|---|
| `orders` | 普通订单（原 MongoDB 动态集合 `orders_{shopKey}` 合并） |
| `unsettled_orders` | 未结算账单（财务流水） |
| `withdraw_records` | 提现记录 |
| `seller_info` | 卖家信息（余额、店铺名） |
| `shop_auth` | OAuth Token 持久化（重启自动恢复，无需重新登录） |
| `sync_checkpoint` | 增量同步进度记录 |

所有多店铺表均含 `shop_key` 列，查询时加 `WHERE shop_key = ?` 即可按店铺过滤。

---

## 同步逻辑

### 触发方式

`KwaiSyncJob` 定时任务，默认每 3 小时执行一次（`application.yml` 中可调整 cron 表达式）。

### 增量同步 + Checkpoint

每次同步不清空数据，使用 Checkpoint 记录上次同步的结束时间戳：

```
effectiveStart = min(上次 checkpoint, now − 7天)
endMs          = now（订单）/ 昨天结束（未结算账单）
```

- **首次同步**（无 checkpoint）：自动回溯 30 天
- **正常运行**（每 3 小时）：滚动窗口 7 天，确保退款状态、结算状态变更能被捕获
- **宕机重启**：从旧 checkpoint 续跑，不丢数据
- **同步失败**：checkpoint 不更新，下次从旧位置重试（upsert 幂等，不会产生重复数据）

### 写入策略

全部使用 `INSERT ... ON DUPLICATE KEY UPDATE`：

- **不可变字段**（`order_no`、`create_time` 等）：只在首次插入时写入，更新时跳过
- **可变字段**（`order_status`、`refund_status` 等）：每次都更新为最新值
- **`update_time`**：使用 `GREATEST(existing, new)` 防止乱序覆盖

### 多店铺并行

所有店铺并发执行，互不等待：

```
shop-A ─── syncShop() ──────────────────────── done ─┐
shop-B ─── syncShop() ─────────────── done ───────────┤ allOf().join()
shop-C ─── syncShop() ──── done ──────────────────────┘
```

单店失败不影响其他店铺，checkpoint 不更新，下次自动重试。

### N+1 消除

未结算订单同步时，`create_time` 通过一次批量 SQL 从本地 `orders` 表查询，不再对每条记录调用 API：

```
旧：for (record : page) → 1 次 API 调用   → N×50 次/轮
新：SELECT order_no, create_time IN (...)  → 1 次 SQL/页
```

---

## 项目结构

```
src/main/java/com/example/kwai_data/
├── client/         快手 API 客户端工厂
├── config/         配置类（KwaiProperties、TimeRangeProvider 等）
├── controller/     HTTP 端点（/debug/db/tables 健康检查）
├── domain/         JPA 实体（对应数据库表）
├── dto/            数据传输对象（API 响应 → 业务对象）
├── facade/         KwaiFacade — 同步主入口，编排各 Service
├── mapper/         DTO ↔ Entity 转换
├── repository/     JPA Repository + ShopAuthRegistry
├── scheduler/      定时任务（KwaiSyncJob、TokenRefreshJob）
├── service/        各类数据同步 Service
└── util/           工具类

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__init_schema.sql   ← Flyway 初始化脚本
```
