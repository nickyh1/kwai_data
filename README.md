# kwai_data

快手小店数据同步服务。通过快手开放平台 API 定期拉取多店铺的订单、提现记录、未结算流水及卖家信息，写入本地 MongoDB（ERP 数据库）。

---

## 目录

- [技术栈](#技术栈)
- [启动方式](#启动方式)
- [配置说明](#配置说明)
- [数据库初始化](#数据库初始化)
- [同步逻辑](#同步逻辑)

---

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 4.0.0 |
| Spring Data MongoDB | 随 Boot 版本 |
| 快手商家开放 SDK | 1.0.7633（本地 jar） |
| Lombok | 1.18.36 |

---

## 启动方式

### 前置条件

- JDK 17+
- MongoDB 运行在 `localhost:27017`（或按实际修改配置）
- Maven（或直接用项目内置的 `mvnw`）

### 本地运行

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

# 3. 编译并启动
./mvnw spring-boot:run
```

启动后应用默认监听 `8080` 端口，健康检查接口：

```
GET http://localhost:8080/actuator/health
```

### 打包运行

```bash
./mvnw package -DskipTests
java -jar target/kwai_data-0.0.1-SNAPSHOT.jar
```

---

## 配置说明

所有配置集中在 `src/main/resources/application.yml`。

```yaml
kwai:
  base-url: https://openapi.kwaixiaodian.com   # 快手开放平台接口地址
  app-key: "your_app_key"
  app-secret: "your_app_secret"
  sign-secret: "your_sign_secret"

  shops:
    # 店铺 key 即为 MongoDB 集合名后缀，可自定义，字母/数字/下划线
    shop-biaowangchangjia:
      access-token: "ChF..."                   # 该店铺的 access_token
    shop-biaowanggongchang:
      access-token: "ChF..."
    # 新增店铺：在此追加一个新 key 即可，无需改代码

  time:
    zone: Asia/Shanghai          # 时区，影响日期边界计算
    lookback-days: 7             # 预留字段，当前未使用

spring:
  mongodb:
    uri: mongodb://localhost:27017/ERP   # 数据库连接串，库名 ERP
    auto-index-creation: true
```

### 关键配置项说明

| 配置项 | 说明 |
|---|---|
| `kwai.app-key` | 快手开放平台应用 Key |
| `kwai.app-secret` | 应用密钥，用于 SDK 鉴权 |
| `kwai.sign-secret` | 签名密钥，用于请求签名 |
| `kwai.shops.<key>.access-token` | 各店铺的授权 Token，过期后需重新授权刷新 |
| `kwai.time.zone` | 日期计算所用时区（默认上海） |
| `spring.mongodb.uri` | MongoDB 连接串，按需修改 host / port / 库名 |

> **生产环境建议**：将 `app-secret`、`sign-secret`、`access-token` 等敏感字段通过环境变量注入，避免明文提交到代码库。  
> 示例：`KWAI_APP_SECRET=xxx java -jar kwai_data.jar`

---

## 数据库初始化

本项目使用 MongoDB，**无需手动建表或执行 DDL**。

- 连接库：`ERP`（由 `spring.mongodb.uri` 指定）
- `spring.mongodb.auto-index-creation: true` 会在首次写入时自动根据实体注解创建索引
- 应用首次启动后，以下集合会自动创建：

| 集合名 | 内容 |
|---|---|
| `seller_info` | 所有店铺的卖家基本信息 + 账户余额 |
| `orders_<shopKey>` | 各店铺订单（按 shopKey 分集合） |
| `Unsetllement__<shopKey>` | 各店铺未结算流水 |
| `Withdraw__<shopKey>` | 各店铺提现记录 |

> `<shopKey>` 对应 `application.yml` 中 `kwai.shops` 下的 key，例如 `shop-biaowangchangjia`。

---

## 同步逻辑

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
    │   ├── orders_<shopKey>
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
        └── 写入 orders_<shopKey>
```

### 时间范围

| 数据类型 | 查询起点 | 查询终点 |
|---|---|---|
| 订单 | 上月 1 日 00:00:00 | 当前时刻 |
| 未结算流水 | 上月 1 日 00:00:00 | 昨日 23:59:59（含） |
| 提现记录 | API 默认 | API 默认 |

订单查询时，时间范围会被切分为多个 **7 天**的子区间，每段独立游标翻页，每页最多 50 条，最多翻 500 页。每次 API 请求间隔 **1 秒**（`Thread.sleep(1000)`），避免触发限流。

### 同步策略：先清后写

每次 `syncAll` 都会先清空相关集合，再全量重写。这是**幂等覆盖**策略，无需维护增量逻辑，但需注意：

- 同步期间集合数据为空，若有下游读取需求，建议错开同步窗口或使用影子集合切换。
- 若某店铺 API 报错，该店铺数据将丢失直到下次同步成功。

### 新增店铺

只需在 `application.yml` 中的 `kwai.shops` 下追加一个新的 key/access-token，重启服务即自动生效，无需改代码。
