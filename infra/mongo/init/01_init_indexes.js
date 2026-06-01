// MongoDB initialization script — runs once on first container start.
// Equivalent to Flyway V1__init_tables.sql for a relational DB.

db = db.getSiblingDB('ERP');

// ── orders ──────────────────────────────────────────────────────────
db.createCollection('orders');
db.orders.createIndex(
  { orderNo: 1 },
  { unique: true, name: 'orders_orderNo_unique' }
);

// ── seller_info ──────────────────────────────────────────────────────
db.createCollection('seller_info');
db.seller_info.createIndex(
  { shopId: 1 },
  { unique: true, name: 'seller_info_shopId_unique' }
);

// ── unsettled_orders ─────────────────────────────────────────────────
db.createCollection('unsettled_orders');
db.unsettled_orders.createIndex(
  { oid: 1 },
  { unique: true, name: 'unsettled_orders_oid_unique' }
);
db.unsettled_orders.createIndex({ orderStatus: 1 },      { name: 'unsettled_orders_orderStatus' });
db.unsettled_orders.createIndex({ settlementStatus: 1 }, { name: 'unsettled_orders_settlementStatus' });
db.unsettled_orders.createIndex({ settlementTime: 1 },   { name: 'unsettled_orders_settlementTime' });
db.unsettled_orders.createIndex({ billTime: 1 },         { name: 'unsettled_orders_billTime' });

// ── withdraw_info ─────────────────────────────────────────────────────
db.createCollection('withdraw_info');

print('ERP database initialized');
