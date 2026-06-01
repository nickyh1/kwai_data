# kwai_data

快手小店数据同步服务。通过快手开放平台 API 定期拉取多店铺的订单、提现记录、未结算流水及卖家信息，写入本地 MongoDB（ERP 数据库）。

---

## 目录

- [技术栈](#技术栈)
- [启动方式](#启动方式)
- [配置方式](#配置方式)
- [数据库初始化](#数据库初始化)
- [同步逻辑说明](#同步逻辑说明)

---

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 4.0.0 |
| Spring Data MongoDB | 随 Boot 版本 |
| MongoDB | 7 |
| 快手商家开放 SDK | 1.0.7633（本地 jar） |
| Lombok | 1.18.36 |

---

## 启动方式

### 方式一：Docker Compose（推荐）

> 一条命令同时启动 MongoDB 数据库和应用，数据库索引自动初始化。

**前置条件**
- Docker & Docker Compose V2（`docker compose` 命令）

**步骤**

```bash
# 1. 复制环境变量模板并填写真实凭证
cp .env.example .env
# 编辑 .env，填入 KWAI_APP_KEY / KWAI_APP_SECRET / KWAI_SIGN_SECRET / 各店铺 Token

# 2. 启动全部服务（首次会自动 build 镜像 + 初始化 MongoDB）
docker compose up -d

# 3. 查看日志
docker compose logs -f app
```

启动后：
- 应用：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- MongoDB：`localhost:27017`（库名 `ERP`）

**停止 / 清理**

```bash
# 停止容器，保留数据卷
docker compose down

# 停止并删除数据卷（数据会丢失，重启后重新初始化）
docker compose down -v
```

---

### 方式二：本地直接运行（需自备 MongoDB）

**前置条件**
- JDK 17+
- MongoDB 7 运行在 `localhost:27017`
- Maven 或使用项目内置的 `mvnw`

**步骤**

```bash
# 1. 进入项目根目录
cd kwai_data

# 2. 安装本地 SDK jar（仅首次需要）
mvn install:install-file \
  -Dfile=lib/kuaishou-merchant-open-sdk-release_open_kwaishop_sdk-1.0.7633.jar \
  -DgroupId=com.kuaishou \
  -DartifactId=merchant-open-sdk \
  -Dversion=1.0.7633 \
  -Dpackaging=jar

# 3. 设置环境变量（或在 IDE Run Configuration 中配置）
export KWAI_APP_KEY=ks_xxx
export KWAI_APP_SECRET=xxx
export KWAI_SIGN_SECRET=xxx
export KWAI_TOKEN_BIAOWANGCHANGJIA=ChF...
export KWAI_TOKEN_BIAOWANGGONGCHANG=ChF...

# 4. 编译并启动
./mvnw spring-boot:run
```

**打包运行**

```bash
./mvnw package -DskipTests
java -jar target/kwai_data-0.0.1-SNAPSHOT.jar
```

---

## 配置方式

所有配置通过**环境变量**注入，开发时写入 `.env`（已加入 `.gitignore`，不会提交到代码库）。

```bash
# 复制模板
cp .env.example .env
```

| 环境变量 | 说明 | 必填 |
|---|---|---|
| `KWAI_APP_KEY` | 快手开放平台应用 Key | 是 |
| `KWAI_APP_SECRET` | 应用密钥，用于 SDK 鉴权 | 是 |
| `KWAI_SIGN_SECRET` | 签名密钥，用于请求签名 | 是 |
| `KWAI_TOKEN_BIAOWANGCHANGJIA` | 标旺厂家店铺 access_token | 是 |
| `KWAI_TOKEN_BIAOWANGGONGCHANG` | 标旺工厂店铺 access_token | 是 |
| `SPRING_DATA_MONGODB_URI` | MongoDB 连接串，Docker 模式由 Compose 自动注入 | 否 |

> **Token 过期**：快手 access_token 有有效期，过期后需重新授权并更新 `.env` 中的对应值，重启服务生效。

### 新增店铺

1. 在 `.env` 中追加 `KWAI_TOKEN_<新店铺KEY>=ChF...`
2. 在 `application.yml` 的 `kwai.shops` 下追加同名 key：
   ```yaml
   kwai:
     shops:
       shop-新店铺key:
         access-token: "${KWAI_TOKEN_新店铺KEY}"
   ```
3. 在 `infra/mongo/init/01_init_indexes.js` 的 `shops` 数组追加新 key（可选，首次同步也会自动建集合）
4. 重启服务即自动同步新店铺，无需改代码

---

## 数据库初始化

本项目使用 **MongoDB**，不使用关系型数据库，因此不使用 Flyway。  
初始化机制等价于 Flyway 的迁移脚本，通过 MongoDB 的 `docker-entrypoint-initdb.d` 机制实现。

### 初始化脚本

`infra/mongo/init/01_init_indexes.js` 会在容器**首次创建数据卷时自动执行一次**（重启不会重复执行）。

脚本执行内容：
- 切换到 `ERP` 数据库
- 创建 `seller_info` 集合并建立 `shopId` 唯一索引
- 为已知店铺预建以下集合及索引：

| 集合名 | 唯一索引字段 | 说明 |
|---|---|---|
| `seller_info` | `shopId` | 所有店铺卖家信息（共用） |
| `orders__<shopKey>` | `orderNo` | 各店铺订单 |
| `Unsetllement__<shopKey>` | `oid` | 各店铺未结算流水（注意双下划线） |
| `Withdraw__<shopKey>` | — | 各店铺提现记录 |

> 集合名规则：`<前缀>_` + `_` + `<shopKey>`，前缀本身带尾部下划线，故集合名为**双下划线**。  
> `<shopKey>` 即 `application.yml` 中 `kwai.shops` 下的 key（如 `shop-biaowangchangjia`）。

### 重新初始化（重置数据）

```bash
# 删除数据卷后重建，脚本会重新执行
docker compose down -v
docker compose up -d
```

### Spring Data 自动索引

`spring.mongodb.auto-index-creation: true` 会在应用启动时根据实体类 `@Indexed` 注解补充创建索引，与初始化脚本互为补充。

---

## 同步逻辑说明

### 触发方式

| 触发 | 类 | 说明 |
|---|---|---|
| 定时任务（主） | `KwaiSyncJob` | 应用启动 **10 秒后**执行首次同步，之后每 **3 小时**固定间隔执行一次 |
| 启动立即执行（可选） | `KwaiStartupRunner` | 默认**已注释**（`@Component` 被移除），取消注释可在启动时同步 |

### 每次同步流程（`KwaiFacade.syncAll`）

```
syncAll()
│
├── 清空 seller_info 集合
│
└── 遍历每个店铺（shop in kwai.shops）
    │
    ├── 清空该店铺的 3 个集合
    │   ├── Unsetllement__<shopKey>
    │   ├── orders__<shopKey>
    │   └── Withdraw__<shopKey>
    │
    ├── 拉取卖家信息 + 账户余额 → 写入 seller_info
    │
    ├── 拉取提现记录（全量）→ 写入 Withdraw__<shopKey>
    │
    ├── 拉取未结算流水（上月初 → 昨日结束，游标分页）
    │   └── 写入 Unsetllement__<shopKey>
    │
    └── 拉取订单（上月初 → 当前时刻，按 7 天分段，游标分页）
        └── 写入 orders__<shopKey>
```

### 时间范围

| 数据类型 | 查询起点 | 查询终点 |
|---|---|---|
| 订单 | 上月 1 日 00:00:00 | 当前时刻 |
| 未结算流水 | 上月 1 日 00:00:00 | 昨日 23:59:59（含） |
| 提现记录 | API 默认 | API 默认 |

订单查询时，时间范围会被切分为多个 **7 天**的子区间，每段独立游标翻页，每页最多 50 条，最多翻 500 页。每次 API 请求间隔 **1 秒**（`Thread.sleep(1000)`），避免触发限流。

### 同步策略：先清后写（幂等覆盖）

每次 `syncAll` 都会先清空相关集合，再全量重写。优点是逻辑简单、无需维护增量状态；注意事项：

- 同步期间集合数据短暂为空，若有下游读取需求，建议错开同步窗口
- 若某店铺 API 报错，该店铺数据将丢失直到下次同步成功
