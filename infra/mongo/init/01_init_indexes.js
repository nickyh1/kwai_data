// MongoDB 初始化脚本 — 容器首次启动时执行一次（等价于关系型 DB 的 Flyway V1 迁移）。
// 挂载路径：/docker-entrypoint-initdb.d/01_init_indexes.js

db = db.getSiblingDB('ERP');

// ── seller_info（所有店铺共用，静态集合）──────────────────────────────
db.createCollection('seller_info');
db.seller_info.createIndex(
  { shopId: 1 },
  { unique: true, name: 'seller_info_shopId_unique' }
);

// ── 以下为已知店铺的动态集合（shop key 来自 application.yml）─────────
// 命名规则：<prefix>_<shopKey>，prefix 含尾部下划线，故实际为双下划线。
// 新增店铺时在此处追加对应三行，或让首次同步自动创建（也可正常运行）。

var shops = [
  'shop-biaowangchangjia',
  'shop-biaowanggongchang'
];

shops.forEach(function(shopKey) {
  // orders__<shopKey>
  var ordersCol = 'orders__' + shopKey;
  db.createCollection(ordersCol);
  db.getCollection(ordersCol).createIndex(
    { orderNo: 1 },
    { unique: true, sparse: true, name: ordersCol + '_orderNo_unique' }
  );

  // Unsetllement__<shopKey>（保留代码中的拼写）
  var unsettledCol = 'Unsetllement__' + shopKey;
  db.createCollection(unsettledCol);
  db.getCollection(unsettledCol).createIndex(
    { oid: 1 },
    { unique: true, sparse: true, name: unsettledCol + '_oid_unique' }
  );
  db.getCollection(unsettledCol).createIndex({ billTime: 1 },         { name: unsettledCol + '_billTime' });
  db.getCollection(unsettledCol).createIndex({ settlementStatus: 1 }, { name: unsettledCol + '_settlementStatus' });

  // Withdraw__<shopKey>
  var withdrawCol = 'Withdraw__' + shopKey;
  db.createCollection(withdrawCol);
});

print('ERP database initialized for shops: ' + shops.join(', '));
