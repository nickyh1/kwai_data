# kwai_data

基于 Spring Boot 的快手小店数据自动同步服务，通过快手商家开放平台 SDK 定时拉取订单、资金、店铺信息，并持久化至 MongoDB，为 ERP 系统提供数据底座。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| 订单同步 | 游标分页拉取指定时间范围内的订单列表，支持上月至今全量同步 |
| 未结算订单 | 拉取未结算账单，并关联订单详情补充 `createTime` |
| 提现记录 | 获取提现流水并写入 MongoDB |
| 店铺信息 | 同步卖家基本信息及账户余额 |
| Token 刷新 | 定时刷新 `access_token`，防止过期（每 24 小时执行） |
| 数据定时同步 | 全量同步任务每 3 小时自动触发 |

---

## 技术栈

- **Java 17**
- **Spring Boot 4.0.0**
- **Spring Data MongoDB** — 数据持久化
- **快手商家开放平台 SDK** v1.0.7633
- **Lombok** — 减少样板代码
- **Jackson / Gson** — JSON 序列化

---

## 项目结构

```
src/main/java/com/example/kwai_data/
├── client/          # SDK 客户端工厂（KwaiClientFactory）
├── config/          # 配置类（KwaiProperties、时间范围等）
├── data/            # 数据传输对象 & MongoDB 文档模型
├── dto/             # 开放平台响应 DTO
├── facade/          # 业务编排层（KwaiFacade）
├── mapper/          # DTO ↔ Doc 转换
├── repository/      # MongoDB Repository & 注册表
├── runner/          # 启动时执行（CommandLineRunner，默认禁用）
├── scheduler/       # 定时任务（同步 & Token 刷新）
└── service/         # 各业务 Service（订单、资金、店铺、Token）
```

---

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.8+
- MongoDB（本地默认端口 `27017`）
- 已在快手商家开放平台创建应用，获取 `appKey`、`appSecret`、`signSecret`

### 配置

编辑 `src/main/resources/application.yml`，填写快手开放平台凭证与店铺 Token：

```yaml
kwai:
  base-url: https://openapi.kwaixiaodian.com
  app-key: "你的 appKey"
  app-secret: "你的 appSecret"
  sign-secret: "你的 signSecret"
  shops:
    shop-xxx:                        # 店铺标识（自定义，唯一）
      access-token: "店铺 accessToken"
      refresh-token: "店铺 refreshToken"
  time:
    zone: Asia/Shanghai
    lookback-days: 7

spring:
  mongodb:
    uri: mongodb://localhost:27017/ERP
```

> **注意**：`access-token` 会由 `TokenRefreshJob` 自动刷新并写回 MongoDB，初次启动需填写有效 Token。

### 构建 & 运行

```bash
# 安装本地 SDK 依赖（仅首次）
mvn install:install-file \
  -Dfile=lib/kuaishou-merchant-open-sdk-release_open_kwaishop_sdk-1.0.7633.jar \
  -DgroupId=com.kuaishou -DartifactId=merchant-open-sdk \
  -Dversion=1.0.7633 -Dpackaging=jar

# 构建并启动
mvn spring-boot:run
```

---

## 定时任务说明

| 任务类 | 触发方式 | 说明 |
|--------|---------|------|
| `KwaiSyncJob` | 启动 10 秒后，每 3 小时 | 全量同步订单、资金、店铺数据 |
| `TokenRefreshJob` | 启动 1 分钟后，每 24 小时 | 刷新所有店铺 access_token |

---

## MongoDB 集合结构

| 集合名 | 内容 |
|--------|------|
| `seller_info` | 店铺基本信息及账户余额 |
| `orders_{shopKey}` | 各店铺订单数据 |
| `Unsetllement_{shopKey}` | 未结算订单 |
| `Withdraw_{shopKey}` | 提现记录 |
| `shop_auth` | Token 持久化（供刷新后更新） |

---

## 多店铺支持

在 `application.yml` 的 `kwai.shops` 下以任意 Key 添加店铺配置即可，系统启动时自动注册至 `ShopAuthRegistry`，同步任务会遍历所有店铺执行。

---

## 健康检查

服务提供 Spring Boot Actuator 端点：

```
GET /actuator/health
GET /actuator/info
```
