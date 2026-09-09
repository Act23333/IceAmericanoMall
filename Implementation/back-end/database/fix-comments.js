const mysql = require('/tmp/node_modules/mysql2/promise');

async function fix() {
  const c = await mysql.createConnection({
    host: '192.168.10.128',
    port: 3306,
    user: 'root',
    password: 'root',
    database: 'icedamericano_mall',
    charset: 'utf8mb4',
  });
  // 先确保客户端编码
  await c.query("SET NAMES utf8mb4");

  // 表注释
  const tableComments = [
    ['address', '用户收货地址表'],
    ['after_sale', '售后申请表'],
    ['cart', '购物车表 — (user_id, sku_id) 唯一去重'],
    ['category', '商品类目表 — 一级分类'],
    ['coupon', '优惠券模板表'],
    ['favorite', '用户收藏表'],
    ['flash_sale', '秒杀活动表'],
    ['home_config', '首页装修配置表'],
    ['operation_log', '用户操作日志'],
    ['order_item', '订单明细表 — SKU 购买快照'],
    ['order_logistics', '物流记录表 — 发货/运输/签收'],
    ['orders', '订单表 — 下单快照存储'],
    ['pay_order', '支付单表 — 微信支付记录'],
    ['points_log', '积分变动日志'],
    ['product', '商品SPU表 — 基本信息与上下架状态'],
    ['review', '商品评价表'],
    ['seller', '商家表 — 店铺信息与资质'],
    ['seller_application', '商家入驻申请表'],
    ['settlement', '商家结算单表'],
    ['sku', 'SKU表 — 商品规格、价格、库存、乐观锁'],
    ['user', '用户表 — 买家/卖家/管理员统一体系'],
    ['user_coupon', '用户优惠券表'],
    ['withdrawal', '商家提现申请表'],
  ];

  console.log('--- 表注释 ---');
  for (const [t, com] of tableComments) {
    try {
      await c.query('ALTER TABLE `' + t + '` COMMENT = ?', [com]);
      console.log('OK  ' + t);
    } catch (e) {
      console.log('ERR ' + t + ': ' + e.message.substring(0, 80));
    }
  }

  console.log('\nDone — 刷新 Navicat 即可看到中文注释');
  await c.end();
}

fix().catch(e => console.error(e));
