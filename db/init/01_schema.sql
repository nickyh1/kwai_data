-- ============================================================
-- 快手数据同步项目 - MySQL 建表脚本
-- 编码：utf8mb4  时间列：DATETIME(3) 存 UTC 时间
-- 执行方式：
--   本地：mysql -u root -p ERP < db/init/01_schema.sql
--   Docker：挂载到 /docker-entrypoint-initdb.d/ 自动执行
-- ============================================================

CREATE DATABASE IF NOT EXISTS ERP CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ERP;

-- ------------------------------------------------------------
-- 订单表
-- 原 MongoDB 动态集合 orders_{shopKey} 合并为单表，用 shop_key 区分
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id                                BIGINT         AUTO_INCREMENT PRIMARY KEY,
    shop_key                          VARCHAR(64)    NOT NULL                    COMMENT '店铺标识',
    order_no                          VARCHAR(64)    NOT NULL                    COMMENT '订单号',
    order_status                      VARCHAR(16)                                COMMENT '订单状态',
    pay_time                          DATETIME(3)                                COMMENT '付款时间（UTC+8 存入）',
    freight                           DECIMAL(12,2)                              COMMENT '运费（元）',
    promotion_discount                DECIMAL(12,2)                              COMMENT '促销减价（元）',
    sub_order_total_price             DECIMAL(12,2)                              COMMENT '子订单商品总价（元）',
    ship_time                         DATETIME(3)                                COMMENT '发货时间',
    refund_initiate_time              DATETIME(3)                                COMMENT '发起退款时间',
    create_time                       DATETIME(3)                                COMMENT '订单创建时间',
    update_time                       DATETIME(3)                                COMMENT '平台最后更新时间（乱序保护用）',
    distribution_type                 VARCHAR(32)                                COMMENT '分销类型',
    receive_time                      DATETIME(3)                                COMMENT '收货时间',
    pay_type                          VARCHAR(32)                                COMMENT '支付方式',
    ks_sku_id                         BIGINT                                     COMMENT '快手 SKU ID',
    product_id                        BIGINT                                     COMMENT '商品 ID',
    product_name                      VARCHAR(512)                               COMMENT '商品名称',
    sku_quantity                      INT                                        COMMENT 'SKU 数量',
    unit_price_before_promo_snapshot  DECIMAL(12,2)                              COMMENT '促销前单价快照（元）',
    discount_amount                   DECIMAL(12,2)                              COMMENT '折扣金额（元）',
    unit_price_snapshot               DECIMAL(12,2)                              COMMENT '单价快照（元）',
    has_refund                        VARCHAR(4)                                 COMMENT '是否有退款：0=无 1=有',
    refund_status                     VARCHAR(16)                                COMMENT '退款状态',
    handling_way                      VARCHAR(16)                                COMMENT '退款处理方式',
    last_ingested_at                  DATETIME(3)                                COMMENT '最后一次同步写入时间',

    UNIQUE KEY uk_shop_order_no       (shop_key, order_no),
    INDEX      idx_shop_create_time   (shop_key, create_time),
    INDEX      idx_shop_update_time   (shop_key, update_time),
    INDEX      idx_shop_order_status  (shop_key, order_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快手订单';

-- ------------------------------------------------------------
-- 未结算订单表
-- 原 MongoDB 动态集合 Unsetllement_{shopKey}
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS unsettled_orders (
    id                              BIGINT         AUTO_INCREMENT PRIMARY KEY,
    shop_key                        VARCHAR(64)    NOT NULL              COMMENT '店铺标识',
    oid                             VARCHAR(64)    NOT NULL              COMMENT '订单 ID',
    order_status                    VARCHAR(16)                          COMMENT '订单状态',
    settlement_status               VARCHAR(16)                          COMMENT '结算状态',
    settlement_time                 DATETIME(3)                          COMMENT '结算时间',
    freight_when_now                DECIMAL(12,2)                        COMMENT '运费（元）',
    bill_time                       DATETIME(3)                          COMMENT '账单时间',
    amount                          DECIMAL(12,2)                        COMMENT '金额（元）',
    platform_commission_amount      DECIMAL(12,2)                        COMMENT '平台佣金（元）',
    create_time                     DATETIME(3)                          COMMENT '订单创建时间',

    UNIQUE KEY uk_shop_oid          (shop_key, oid),
    INDEX idx_shop_bill_time        (shop_key, bill_time),
    INDEX idx_settlement_status     (shop_key, settlement_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未结算订单';

-- ------------------------------------------------------------
-- 提现记录表
-- 原 MongoDB 动态集合 Withdraw_{shopKey}
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS withdraw_records (
    id                      BIGINT         AUTO_INCREMENT PRIMARY KEY,
    shop_key                VARCHAR(64)    NOT NULL              COMMENT '店铺标识',
    platform_withdraw_no    VARCHAR(64)    NOT NULL              COMMENT '平台提现单号',
    withdraw_time           DATETIME(3)                          COMMENT '提现发起时间',
    received_time           DATETIME(3)                          COMMENT '到账时间',
    state                   VARCHAR(16)                          COMMENT '提现状态',
    withdraw_money          BIGINT                               COMMENT '提现金额（分）',
    fail_reason             VARCHAR(256)                         COMMENT '失败原因',
    withdraw_desc           VARCHAR(256)                         COMMENT '提现描述',
    update_time             DATETIME(3)                          COMMENT '记录更新时间',

    UNIQUE KEY uk_shop_withdraw_no  (shop_key, platform_withdraw_no),
    INDEX idx_withdraw_time         (shop_key, withdraw_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现记录';

-- ------------------------------------------------------------
-- 卖家信息表
-- 原 MongoDB 集合 seller_info（共用，不按店铺隔离）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seller_info (
    id                      BIGINT         AUTO_INCREMENT PRIMARY KEY,
    shop_id                 BIGINT         NOT NULL              COMMENT '快手店铺 ID',
    shop_name               VARCHAR(256)                         COMMENT '店铺名称',
    account_balance         BIGINT                               COMMENT '账户余额（分）',
    pending_settlement      DECIMAL(16,2)                        COMMENT '待结算金额（元）',
    total_withdrawn         DECIMAL(16,2)                        COMMENT '已提现金额（元）',
    net_transaction_amount  DECIMAL(16,2)                        COMMENT '净交易额（元）',
    update_time             DATETIME(3)                          COMMENT '更新时间',

    UNIQUE KEY uk_shop_id   (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺卖家信息';

-- ------------------------------------------------------------
-- 店铺 OAuth 认证信息表
-- 原 MongoDB 集合 shop_auth，用于持久化 token、支持重启恢复
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shop_auth (
    shop_key            VARCHAR(64)    NOT NULL PRIMARY KEY    COMMENT '店铺标识（YAML key）',
    access_token        TEXT                                   COMMENT 'access_token',
    refresh_token       TEXT                                   COMMENT 'refresh_token',
    expires_at          DATETIME(3)                            COMMENT 'token 过期时间',
    last_refreshed_at   DATETIME(3)                            COMMENT '最后刷新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺 OAuth 认证信息';
