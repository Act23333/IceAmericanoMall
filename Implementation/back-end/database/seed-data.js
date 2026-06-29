/**
 * 冰美商城 — 种子数据脚本
 *
 * 用法: node seed-data.js
 *
 * 结果:
 * - 2 个用户 (买家 buy123 + 卖家 sell123, 密码均为 Test1234)
 * - 6 个一级分类
 * - 2 个商家
 * - 16 款商品 + 每款 2-3 SKU
 * - 测试收货地址
 * - 图片: 京东 CDN 占位图
 */

const mysql = require('/tmp/node_modules/mysql2/promise');
const bcrypt = require('/tmp/node_modules/bcryptjs');

const DB = {
  host: '192.168.10.128',
  port: 3306,
  user: 'root',
  password: 'root',
  database: 'icedamericano_mall',
};

/* ================================================================
   JD CDN 图片 — 各类目真实商品图
   ================================================================ */
const JD_IMG = {
  // 居家生活
  cup: 'https://img10.360buyimg.com/n1/s200x200_jfs/t1/234567/12/3456/123456/667a8b9cF0d1e2f3a.jpg',
  lamp: 'https://img12.360buyimg.com/n1/s200x200_jfs/t1/123456/34/5678/234567/667b9c0dF1e2f3a4b.jpg',
  blanket: 'https://img11.360buyimg.com/n1/s200x200_jfs/t1/345678/56/7890/345678/667c0d1eF2f3a4b5c.jpg',
  vase: 'https://img13.360buyimg.com/n1/s200x200_jfs/t1/456789/78/9012/456789/667d1e2fF3a4b5c6d.jpg',
  candle: 'https://img14.360buyimg.com/n1/s200x200_jfs/t1/567890/90/1234/567890/667e2f3aF4b5c6d7e.jpg',
  basket: 'https://img10.360buyimg.com/n1/s200x200_jfs/t1/678901/12/3456/678901/667f3a4bF5c6d7e8f.jpg',

  // 服饰穿搭
  scarf: 'https://img11.360buyimg.com/n1/s200x200_jfs/t1/789012/34/5678/789012/668a4b5cF6d7e8f9a.jpg',
  bag: 'https://img12.360buyimg.com/n1/s200x200_jfs/t1/890123/56/7890/890123/668b5c6dF7e8f9a0b.jpg',
  watch: 'https://img13.360buyimg.com/n1/s200x200_jfs/t1/901234/78/9012/901234/668c6d7eF8f9a0b1c.jpg',

  // 数码好物
  earphone: 'https://img14.360buyimg.com/n1/s200x200_jfs/t1/112345/90/1234/112345/668d7e8fF9a0b1c2d.jpg',
  charger: 'https://img10.360buyimg.com/n1/s200x200_jfs/t1/223456/12/3456/223456/668e8f9aF0b1c2d3e.jpg',
  speaker: 'https://img11.360buyimg.com/n1/s200x200_jfs/t1/334567/34/5678/334567/668f9a0bF1c2d3e4f.jpg',

  // 美食饮品
  tea: 'https://img12.360buyimg.com/n1/s200x200_jfs/t1/445678/56/7890/445678/669a0b1cF2d3e4f5a.jpg',
  coffee: 'https://img13.360buyimg.com/n1/s200x200_jfs/t1/556789/78/9012/556789/669b1c2dF3e4f5a6b.jpg',

  // 文具书籍
  notebook: 'https://img14.360buyimg.com/n1/s200x200_jfs/t1/667890/90/1234/667890/669c2d3eF4f5a6b7c.jpg',
  pen: 'https://img10.360buyimg.com/n1/s200x200_jfs/t1/778901/12/3456/778901/669d3e4fF5a6b7c8d.jpg',

  // 运动户外
  bottle: 'https://img11.360buyimg.com/n1/s200x200_jfs/t1/889012/34/5678/889012/669e4f5aF6b7c8d9e.jpg',
  yoga: 'https://img12.360buyimg.com/n1/s200x200_jfs/t1/990123/56/7890/990123/669f5a6bF7c8d9e0f.jpg',
};

/* ================================================================
   数据定义
   ================================================================ */

const CATEGORIES = [
  { id: 1, name: '居家生活', parentId: null, level: 1, sortOrder: 1 },
  { id: 2, name: '服饰穿搭', parentId: null, level: 1, sortOrder: 2 },
  { id: 3, name: '数码好物', parentId: null, level: 1, sortOrder: 3 },
  { id: 4, name: '美食饮品', parentId: null, level: 1, sortOrder: 4 },
  { id: 5, name: '文具书籍', parentId: null, level: 1, sortOrder: 5 },
  { id: 6, name: '运动户外', parentId: null, level: 1, sortOrder: 6 },
];

const USERS = [
  { userId: 'u_buyer_001', username: 'buy123', phone: '13800001111', roleType: 1, status: 1, balance: 100000 },
  { userId: 'u_seller_001', username: 'sell123', phone: '13800002222', roleType: 2, status: 1, balance: 0 },
  { userId: 'u_seller_002', username: 'sell456', phone: '13800003333', roleType: 2, status: 1, balance: 0 },
  { userId: 'u_admin_001', username: 'admin', phone: '13800000000', roleType: 3, status: 1, balance: 0 },
];

const SELLERS = [
  { businessUserId: 'u_seller_001', shopName: '冰美精选家居', contactPhone: '13800002222', province: '浙江省', city: '杭州市', district: '西湖区', detailAddress: '文三路 138 号', status: 1 },
  { businessUserId: 'u_seller_002', shopName: '数码生活馆', contactPhone: '13800003333', province: '广东省', city: '深圳市', district: '南山区', detailAddress: '科技园路 88 号', status: 1 },
];

const ADDRESSES = [
  { businessUserId: 'u_buyer_001', receiver: '张三', phone: '13800001111', province: '北京市', city: '北京市', district: '朝阳区', street: '建国路', detail: '88 号 1201 室', isDefault: 1, label: '家' },
  { businessUserId: 'u_buyer_001', receiver: '张三', phone: '13800001111', province: '上海市', city: '上海市', district: '浦东新区', street: '张江路', detail: '科技园区 5 号楼', isDefault: 0, label: '公司' },
];

// 每个 catId 对应 2-3 款商品
const PRODUCTS = [
  // 居家生活 (catId=1)
  { productId: 'prod_001', sellerId: 1, categoryId: 1, name: '手工陶瓷茶杯 — 天青釉', brand: '冰美精选', mainImage: JD_IMG.cup, description: '景德镇匠人手工拉坯，1280°C 高温烧制。杯壁轻薄如纸，釉色温润如玉。', soldCount: 1280, commentCount: 86, status: 1,
    skus: [{ skuId: 'sku_001', spec: '天青 / 标准 (200ml)', price: 12800, stock: 50, soldCount: 320, status: 1 },
           { skuId: 'sku_002', spec: '月白 / 标准 (200ml)', price: 12800, stock: 30, soldCount: 180, status: 1 },
           { skuId: 'sku_003', spec: '天青 / 大号 (350ml)', price: 16800, stock: 20, soldCount: 90, status: 1 }] },
  { productId: 'prod_002', sellerId: 1, categoryId: 1, name: '原木台灯 — 黑胡桃木', brand: '冰美精选', mainImage: JD_IMG.lamp, description: '北美黑胡桃木底座，暖白 LED 光源，触摸三档调光。为你的书桌添一份自然的温度。', soldCount: 860, commentCount: 52, status: 1,
    skus: [{ skuId: 'sku_004', spec: '黑胡桃木 / 暖白光', price: 26800, stock: 35, soldCount: 200, status: 1 }] },
  { productId: 'prod_003', sellerId: 1, categoryId: 1, name: '亚麻编织毯 — 天然本色', brand: '冰美精选', mainImage: JD_IMG.blanket, description: '100% 欧洲亚麻，手工编织。透气吸湿，四季皆宜。可作沙发毯、床尾毯、野餐垫。', soldCount: 450, commentCount: 31, status: 1,
    skus: [{ skuId: 'sku_005', spec: '米白 / 130×170cm', price: 18800, stock: 40, soldCount: 150, status: 1 },
           { skuId: 'sku_006', spec: '浅灰 / 130×170cm', price: 18800, stock: 25, soldCount: 80, status: 1 },
           { skuId: 'sku_007', spec: '米白 / 170×230cm', price: 25800, stock: 15, soldCount: 60, status: 1 }] },

  // 服饰穿搭 (catId=2)
  { productId: 'prod_004', sellerId: 2, categoryId: 2, name: '棉麻围巾 — 手工植物染', brand: '冰美精选', mainImage: JD_IMG.scarf, description: '新疆长绒棉 + 亚麻混纺，天然靛蓝植物染色。柔和不刺眼，越洗越有味道。', soldCount: 2100, commentCount: 145, status: 1,
    skus: [{ skuId: 'sku_008', spec: '靛蓝 / 180×50cm', price: 8900, stock: 80, soldCount: 900, status: 1 },
           { skuId: 'sku_009', spec: '茜草红 / 180×50cm', price: 8900, stock: 60, soldCount: 500, status: 1 }] },
  { productId: 'prod_005', sellerId: 2, categoryId: 2, name: '手工皮革托特包', brand: '冰美精选 × 植鞣革', mainImage: JD_IMG.bag, description: '意大利植鞣革，手工缝线。随着使用会逐渐包浆变色，成为你独一无二的伴侣。', soldCount: 620, commentCount: 48, status: 1,
    skus: [{ skuId: 'sku_010', spec: '棕色 / 中号', price: 39800, stock: 15, soldCount: 120, status: 1 },
           { skuId: 'sku_011', spec: '黑色 / 大号', price: 45800, stock: 10, soldCount: 80, status: 1 }] },
  { productId: 'prod_006', sellerId: 2, categoryId: 2, name: '极简腕表 — 超薄石英', brand: '冰美精选', mainImage: JD_IMG.watch, description: '日本精工石英机芯，316L 不锈钢表壳，蓝宝石玻璃表镜。厚度仅 5.8mm。', soldCount: 340, commentCount: 28, status: 1,
    skus: [{ skuId: 'sku_012', spec: '银色 / 黑色表带', price: 69800, stock: 20, soldCount: 90, status: 1 },
           { skuId: 'sku_013', spec: '玫瑰金 / 棕色表带', price: 76800, stock: 12, soldCount: 55, status: 1 }] },

  // 数码好物 (catId=3)
  { productId: 'prod_007', sellerId: 2, categoryId: 3, name: '真无线降噪耳机 — 40dB ANC', brand: '冰美声学', mainImage: JD_IMG.earphone, description: '混合主动降噪，40dB 深度。LDAC 高清传输，40 小时续航。IPX5 防水。', soldCount: 3200, commentCount: 256, status: 1,
    skus: [{ skuId: 'sku_014', spec: '曜石黑', price: 59900, stock: 100, soldCount: 1500, status: 1 },
           { skuId: 'sku_015', spec: '云雾白', price: 59900, stock: 80, soldCount: 1200, status: 1 }] },
  { productId: 'prod_008', sellerId: 2, categoryId: 3, name: '氮化镓充电器 — 65W 三口', brand: '冰美科技', mainImage: JD_IMG.charger, description: 'GaN 第三代半导体，65W 大功率，2C1A 三口同时充。比普通 65W 小 40%。', soldCount: 1800, commentCount: 132, status: 1,
    skus: [{ skuId: 'sku_016', spec: '白色 / 65W', price: 12900, stock: 150, soldCount: 800, status: 1 }] },
  { productId: 'prod_009', sellerId: 2, categoryId: 3, name: '便携蓝牙音箱 — 360°环绕声', brand: '冰美声学', mainImage: JD_IMG.speaker, description: '360° 全向发声，IP67 防尘防水，20 小时续航。户外露营必备。', soldCount: 950, commentCount: 78, status: 1,
    skus: [{ skuId: 'sku_017', spec: '森林绿', price: 29900, stock: 45, soldCount: 300, status: 1 },
           { skuId: 'sku_018', spec: '午夜蓝', price: 29900, stock: 35, soldCount: 250, status: 1 }] },

  // 美食饮品 (catId=4)
  { productId: 'prod_010', sellerId: 1, categoryId: 4, name: '明前龙井 — 西湖一级产区', brand: '冰美茶集', mainImage: JD_IMG.tea, description: '西湖龙井一级保护区，明前采摘。豆香浓郁，回甘悠长。50g 小罐装，锁住新鲜。', soldCount: 560, commentCount: 67, status: 1,
    skus: [{ skuId: 'sku_019', spec: '50g 小罐装', price: 19800, stock: 30, soldCount: 200, status: 1 },
           { skuId: 'sku_020', spec: '100g 礼盒装', price: 35800, stock: 15, soldCount: 80, status: 1 }] },
  { productId: 'prod_011', sellerId: 1, categoryId: 4, name: '手冲咖啡豆 — 耶加雪菲 G1', brand: '冰美咖啡', mainImage: JD_IMG.coffee, description: '埃塞俄比亚耶加雪菲 G1，水洗处理。柑橘、茉莉花香，明亮酸质。烘焙日期 7 天内。', soldCount: 780, commentCount: 92, status: 1,
    skus: [{ skuId: 'sku_021', spec: '浅烘 / 200g', price: 6800, stock: 60, soldCount: 350, status: 1 },
           { skuId: 'sku_022', spec: '中烘 / 200g', price: 6800, stock: 45, soldCount: 280, status: 1 }] },

  // 文具书籍 (catId=5)
  { productId: 'prod_012', sellerId: 1, categoryId: 5, name: '手工绑带笔记本 — A5 方格', brand: '冰美文具', mainImage: JD_IMG.notebook, description: '意大利进口植鞣革封面，可替换内芯。80g 无酸纸，不洇墨。', soldCount: 1200, commentCount: 105, status: 1,
    skus: [{ skuId: 'sku_023', spec: '棕色 / 方格内页', price: 12800, stock: 70, soldCount: 600, status: 1 },
           { skuId: 'sku_024', spec: '黑色 / 空白内页', price: 12800, stock: 50, soldCount: 400, status: 1 }] },
  { productId: 'prod_013', sellerId: 1, categoryId: 5, name: '黄铜制图笔 — 0.5mm', brand: '冰美文具', mainImage: JD_IMG.pen, description: '全黄铜笔身，德国施密特笔芯。随着使用形成独特包浆，越用越有味道。', soldCount: 680, commentCount: 44, status: 1,
    skus: [{ skuId: 'sku_025', spec: '黄铜原色 / 0.5mm', price: 4800, stock: 100, soldCount: 400, status: 1 }] },

  // 运动户外 (catId=6)
  { productId: 'prod_014', sellerId: 2, categoryId: 6, name: '钛合金运动水壶 — 500ml', brand: '冰美户外', mainImage: JD_IMG.bottle, description: '纯钛材质，重量仅 120g。无毒无味，不串味。双层真空保冷 12 小时。', soldCount: 1500, commentCount: 120, status: 1,
    skus: [{ skuId: 'sku_026', spec: '钛原色 / 500ml', price: 22800, stock: 55, soldCount: 700, status: 1 },
           { skuId: 'sku_027', spec: '钛原色 / 750ml', price: 26800, stock: 30, soldCount: 350, status: 1 }] },
  { productId: 'prod_015', sellerId: 2, categoryId: 6, name: '天然橡胶瑜伽垫 — 5mm', brand: '冰美运动', mainImage: JD_IMG.yoga, description: '斯里兰卡天然橡胶，PU 面层。干湿防滑，回弹出色。可降解环保。', soldCount: 2300, commentCount: 189, status: 1,
    skus: [{ skuId: 'sku_028', spec: '深灰 / 5mm / 183×68cm', price: 32800, stock: 40, soldCount: 1000, status: 1 },
           { skuId: 'sku_029', spec: '墨绿 / 5mm / 183×68cm', price: 32800, stock: 25, soldCount: 650, status: 1 }] },

  // 花瓶 — 单独一款
  { productId: 'prod_016', sellerId: 1, categoryId: 1, name: '日式手工玻璃花瓶', brand: '冰美精选', mainImage: JD_IMG.vase, description: '东京手工吹制玻璃，每一只都独一无二。适合单枝花或小型花束插放。', soldCount: 380, commentCount: 26, status: 1,
    skus: [{ skuId: 'sku_030', spec: '透明 / 高 20cm', price: 9800, stock: 25, soldCount: 200, status: 1 }] },
];


/* ================================================================
   主流程
   ================================================================ */

async function seed() {
  const conn = await mysql.createConnection(DB);
  console.log('✅ 已连接 MySQL');

  const now = new Date().toISOString().slice(0, 19).replace('T', ' ');
  const PASS = bcrypt.hashSync('Test1234', 10);

  // ── 用户 ──
  console.log('\n--- 用户 ---');
  const userIdMap = {}; // businessId → autoIncrement id
  for (const u of USERS) {
    const [result] = await conn.query(
      `INSERT INTO user (user_id, username, phone, password, role_type, status, balance, register_time, create_time, update_time)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [u.userId, u.username, u.phone, PASS, u.roleType, u.status, u.balance, now, now, now]
    );
    userIdMap[u.userId] = result.insertId;
    console.log(`  user: ${u.username} (id=${result.insertId}, ${u.roleType === 1 ? '买家' : u.roleType === 2 ? '卖家' : '管理员'})`);
  }

  // ── 分类 ──
  console.log('\n--- 分类 ---');
  for (const c of CATEGORIES) {
    await conn.query(
      `INSERT INTO category (id, name, parent_id, level, sort_order, create_time, update_time) VALUES (?,?,?,?,?,?,?)`,
      [c.id, c.name, c.parentId, c.level, c.sortOrder, now, now]
    );
    console.log(`  ${c.name}`);
  }

  // ── 商家 ──
  console.log('\n--- 商家 ---');
  const sellerIdMap = {}; // index → autoIncrement id
  let si = 1;
  for (const s of SELLERS) {
    const realUserId = userIdMap[s.businessUserId];
    const [result] = await conn.query(
      `INSERT INTO seller (user_id, shop_name, contact_phone, province, city, district, detail_address, status, create_time, update_time)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
      [realUserId, s.shopName, s.contactPhone, s.province, s.city, s.district, s.detailAddress, s.status, now, now]
    );
    sellerIdMap[si++] = result.insertId;
    console.log(`  ${s.shopName} (seller.id=${result.insertId})`);
  }

  // ── 商品 + SKU ──
  console.log('\n--- 商品 ---');
  let totalSkus = 0;
  for (const p of PRODUCTS) {
    const realSellerId = sellerIdMap[p.sellerId];
    const [result] = await conn.query(
      `INSERT INTO product (product_id, seller_id, category_id, name, brand, main_image, description, sold_count, comment_count, is_ad, status, publish_time, create_time, update_time)
       VALUES (?,?,?,?,?,?,?,?,?,0,?,?,?,?)`,
      [p.productId, realSellerId, p.categoryId, p.name, p.brand, p.mainImage, p.description, p.soldCount, p.commentCount, p.status, now, now, now]
    );
    const dbProductId = result.insertId;

    for (const sku of p.skus) {
      await conn.query(
        `INSERT INTO sku (sku_id, product_id, spec, price, stock, image, sold_count, status, version, create_time, update_time)
         VALUES (?,?,?,?,?,?,?,?,0,?,?)`,
        [sku.skuId, dbProductId, sku.spec, sku.price, sku.stock, p.mainImage, sku.soldCount, sku.status, now, now]
      );
      totalSkus++;
    }
    console.log(`  ${p.name} (+${p.skus.length} SKU)`);
  }
  console.log(`  总计: ${PRODUCTS.length} 款商品, ${totalSkus} 个 SKU`);

  // ── 地址 ──
  console.log('\n--- 地址 ---');
  for (const a of ADDRESSES) {
    const realUserId = userIdMap[a.businessUserId];
    await conn.query(
      `INSERT INTO address (user_id, receiver, phone, province, city, district, street, detail, is_default, label, create_time, update_time)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`,
      [realUserId, a.receiver, a.phone, a.province, a.city, a.district, a.street, a.detail, a.isDefault, a.label, now, now]
    );
    console.log(`  ${a.receiver} — ${a.province}${a.city} ${a.detail} ${a.isDefault ? '(默认)' : ''}`);
  }

  await conn.end();
  console.log('\n✅ 种子数据写入完成!');
  console.log('  买家: buy123 / Test1234');
  console.log('  卖家: sell123 / Test1234');
}

seed().catch(e => { console.error('❌', e); process.exit(1); });
