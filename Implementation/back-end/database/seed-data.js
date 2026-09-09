/**
 * 冰美商城 — 种子数据脚本 V5.0
 *
 * 用法: node seed-data.js
 *
 * 图片: picsum.photos 真实照片 CDN，同一 seed 永远返回同一张图
 * 数据库: 本地 Windows MySQL
 */

const mysql = require('mysql2/promise');
const bcrypt = require('bcryptjs');

const DB = {
  host: '192.168.10.128',
  port: 3306,
  user: 'root',
  password: 'root',
  database: 'icedamericano_mall',
};

/* ================================================================
   图片生成函数 — picsum.photos (全球CDN, 真实照片, seed固定)
   ================================================================ */
function mainImg(seed)  { return `https://picsum.photos/seed/${seed}/400/400`; }
function gallery(seed)  { return JSON.stringify([
  `https://picsum.photos/seed/${seed}a/800/800`,
  `https://picsum.photos/seed/${seed}b/800/800`,
  `https://picsum.photos/seed/${seed}c/800/800`
]); }
function skuImg(seed)   { return `https://picsum.photos/seed/${seed}/400/400`; }

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

// seed必须纯字母数字, picsum不支持连字符
const S = (n) => 'p' + n; // product seed: p1, p2, ...
const K = (n) => 's' + n; // sku seed: s1, s2, ...

let pi = 0, ki = 0;
const P = (seed, sellerId, categoryId, name, brand, desc, sold, cmt) => ({
  productId: `prod_${String(++pi).padStart(3,'0')}`, sellerId, categoryId, name, brand,
  imgSeed: S(pi), description: desc, soldCount: sold, commentCount: cmt, status: 1, skus: []
});
const SKU = (spec, price, origPrice, stock, sold) => ({
  skuId: `sku_${String(++ki).padStart(3,'0')}`, spec, price, originalPrice: origPrice,
  stock, soldCount: sold, status: 1, imgSeed: K(ki)
});

const PRODUCTS = [];
// 居家生活 (catId=1)
let p = P(S(1), 1, 1, '手工陶瓷茶杯 — 天青釉', '冰美精选', '景德镇匠人手工拉坯，1280°C 高温烧制。杯壁轻薄如纸，釉色温润如玉。', 1280, 86);
p.skus.push(SKU('天青 / 标准 (200ml)', 12800, 15800, 50, 320), SKU('月白 / 标准 (200ml)', 12800, 15800, 30, 180), SKU('天青 / 大号 (350ml)', 16800, 19800, 20, 90));
PRODUCTS.push(p);

p = P(S(1), 1, 1, '原木台灯 — 黑胡桃木', '冰美精选', '北美黑胡桃木底座，暖白 LED 光源，触摸三档调光。', 860, 52);
p.skus.push(SKU('黑胡桃木 / 暖白光', 26800, 29800, 35, 200)); PRODUCTS.push(p);

p = P(S(1), 1, 1, '亚麻编织毯 — 天然本色', '冰美精选', '100% 欧洲亚麻，手工编织。透气吸湿，四季皆宜。', 450, 31);
p.skus.push(SKU('米白 / 130×170cm', 18800, 22800, 40, 150), SKU('浅灰 / 130×170cm', 18800, 22800, 25, 80), SKU('米白 / 170×230cm', 25800, 29800, 15, 60));
PRODUCTS.push(p);

// 服饰穿搭 (catId=2)
p = P(S(1), 2, 2, '棉麻围巾 — 手工植物染', '冰美精选', '新疆长绒棉 + 亚麻混纺，天然靛蓝植物染色。', 2100, 145);
p.skus.push(SKU('靛蓝 / 180×50cm', 8900, 12800, 80, 900), SKU('茜草红 / 180×50cm', 8900, 12800, 60, 500)); PRODUCTS.push(p);

p = P(S(1), 2, 2, '手工皮革托特包', '冰美精选 × 植鞣革', '意大利植鞣革，手工缝线。随使用逐渐包浆变色。', 620, 48);
p.skus.push(SKU('棕色 / 中号', 39800, 45800, 15, 120), SKU('黑色 / 大号', 45800, 52800, 10, 80)); PRODUCTS.push(p);

p = P(S(1), 2, 2, '极简腕表 — 超薄石英', '冰美精选', '日本精工石英机芯，316L 不锈钢表壳，蓝宝石玻璃表镜。', 340, 28);
p.skus.push(SKU('银色 / 黑色表带', 69800, 79800, 20, 90), SKU('玫瑰金 / 棕色表带', 76800, 86800, 12, 55)); PRODUCTS.push(p);

// 数码好物 (catId=3)
p = P(S(1), 2, 3, '真无线降噪耳机 — 40dB ANC', '冰美声学', '混合主动降噪，40dB 深度。LDAC 高清传输，40h续航。', 3200, 256);
p.skus.push(SKU('曜石黑', 59900, 69900, 100, 1500), SKU('云雾白', 59900, 69900, 80, 1200)); PRODUCTS.push(p);

p = P(S(1), 2, 3, '氮化镓充电器 — 65W 三口', '冰美科技', 'GaN 第三代半导体，65W 大功率，2C1A 三口同时充。', 1800, 132);
p.skus.push(SKU('白色 / 65W', 12900, 15900, 150, 800)); PRODUCTS.push(p);

p = P(S(1), 2, 3, '便携蓝牙音箱 — 360°环绕声', '冰美声学', '360°全向发声，IP67 防尘防水，20h续航。', 950, 78);
p.skus.push(SKU('森林绿', 29900, 35900, 45, 300), SKU('午夜蓝', 29900, 35900, 35, 250)); PRODUCTS.push(p);

// 美食饮品 (catId=4)
p = P(S(1), 1, 4, '明前龙井 — 西湖一级产区', '冰美茶集', '西湖龙井一级保护区，明前采摘。豆香浓郁，回甘悠长。', 560, 67);
p.skus.push(SKU('50g 小罐装', 19800, 25800, 30, 200), SKU('100g 礼盒装', 35800, 42800, 15, 80)); PRODUCTS.push(p);

p = P(S(1), 1, 4, '手冲咖啡豆 — 耶加雪菲 G1', '冰美咖啡', '埃塞俄比亚耶加雪菲 G1，水洗处理。柑橘、茉莉花香。', 780, 92);
p.skus.push(SKU('浅烘 / 200g', 6800, 8800, 60, 350), SKU('中烘 / 200g', 6800, 8800, 45, 280)); PRODUCTS.push(p);

// 文具书籍 (catId=5)
p = P(S(1), 1, 5, '手工绑带笔记本 — A5 方格', '冰美文具', '意大利植鞣革封面，可替换内芯。80g无酸纸。', 1200, 105);
p.skus.push(SKU('棕色 / 方格内页', 12800, 15800, 70, 600), SKU('黑色 / 空白内页', 12800, 15800, 50, 400)); PRODUCTS.push(p);

p = P(S(1), 1, 5, '黄铜制图笔 — 0.5mm', '冰美文具', '全黄铜笔身，德国施密特笔芯。随使用形成独特包浆。', 680, 44);
p.skus.push(SKU('黄铜原色 / 0.5mm', 4800, 6800, 100, 400)); PRODUCTS.push(p);

// 运动户外 (catId=6)
p = P(S(1), 2, 6, '钛合金运动水壶 — 500ml', '冰美户外', '纯钛材质，120g。双层真空保冷12h。', 1500, 120);
p.skus.push(SKU('钛原色 / 500ml', 22800, 26800, 55, 700), SKU('钛原色 / 750ml', 26800, 30800, 30, 350)); PRODUCTS.push(p);

p = P(S(1), 2, 6, '天然橡胶瑜伽垫 — 5mm', '冰美运动', '斯里兰卡天然橡胶，PU面层。干湿防滑，可降解。', 2300, 189);
p.skus.push(SKU('深灰 / 5mm', 32800, 39800, 40, 1000), SKU('墨绿 / 5mm', 32800, 39800, 25, 650)); PRODUCTS.push(p);

// 花瓶
p = P(S(1), 1, 1, '日式手工玻璃花瓶', '冰美精选', '东京手工吹制玻璃，每一只都独一无二。', 380, 26);
p.skus.push(SKU('透明 / 高 20cm', 9800, 12800, 25, 200)); PRODUCTS.push(p);


/* ================================================================
   主流程
   ================================================================ */

async function seed() {
  const conn = await mysql.createConnection(DB);
  console.log('✅ 已连接 MySQL');

  // 先清空所有业务表（按FK依赖顺序）
  console.log('清空旧数据...');
  await conn.execute('SET FOREIGN_KEY_CHECKS=0');
  const [tables] = await conn.query('SHOW TABLES');
  for (const row of tables) {
    const t = Object.values(row)[0];
    try { await conn.execute(`DELETE FROM \`${t}\``); } catch {}
    try { await conn.execute(`ALTER TABLE \`${t}\` AUTO_INCREMENT=1`); } catch {}
  }
  await conn.execute('SET FOREIGN_KEY_CHECKS=1');
  console.log('旧数据已清空');

  const now = new Date().toISOString().slice(0, 19).replace('T', ' ');
  const PASS = bcrypt.hashSync('Test1234', 10);

  // ── 用户 ──
  console.log('\n--- 用户 ---');
  const userIdMap = {};
  for (const u of USERS) {
    const [result] = await conn.query(
      `INSERT INTO user (user_id, username, phone, password, role_type, status, balance, register_time, create_time, update_time)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [u.userId, u.username, u.phone, PASS, u.roleType, u.status, u.balance, now, now, now]
    );
    userIdMap[u.userId] = result.insertId;
    console.log(`  user: ${u.username} (id=${result.insertId})`);
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
  const sellerIdMap = {};
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
    const imgMain = mainImg(p.imgSeed);
    const imgGallery = gallery(p.imgSeed);
    const [result] = await conn.query(
      `INSERT INTO product (product_id, seller_id, category_id, name, brand, main_image, images, description, sold_count, comment_count, is_ad, status, publish_time, create_time, update_time)
       VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?,?,?)`,
      [p.productId, realSellerId, p.categoryId, p.name, p.brand, imgMain, imgGallery, p.description, p.soldCount, p.commentCount, p.status, now, now, now]
    );
    const dbProductId = result.insertId;

    for (const sku of p.skus) {
      await conn.query(
        `INSERT INTO sku (sku_id, product_id, spec, price, original_price, stock, image, sold_count, status, version, create_time, update_time)
         VALUES (?,?,?,?,?,?,?,?,?,0,?,?)`,
        [sku.skuId, dbProductId, sku.spec, sku.price, sku.originalPrice, sku.stock, skuImg(sku.imgSeed), sku.soldCount, sku.status, now, now]
      );
      totalSkus++;
    }
    console.log(`  ${p.name} (+${p.skus.length} SKU) — ${imgMain}`);
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
    console.log(`  ${a.receiver} — ${a.province}${a.city} ${a.detail}`);
  }

  await conn.end();
  console.log('\n✅ 种子数据写入完成!');
  console.log('  买家: buy123 / Test1234');
  console.log('  卖家: sell123 / Test1234');
}

seed().catch(e => { console.error('❌', e); process.exit(1); });
