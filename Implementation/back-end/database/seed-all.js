/**
 * 冰美商城 — MySQL + ES 全量种子数据
 *
 * 用法: node seed-all.js
 *
 * 结果:
 *   MySQL: 4 用户 + 6 分类 + 2 商家 + 16 商品 + 30 SKU + 2 地址
 *   ES:    products 索引, 16 文档
 */

const mysql = require('/tmp/node_modules/mysql2/promise');
const { Client } = require('/tmp/node_modules/@elastic/elasticsearch');
const bcrypt = require('/tmp/node_modules/bcryptjs');

const MYSQL = { host: '192.168.10.128', port: 3306, user: 'root', password: 'root', database: 'icedamericano_mall' };
const es = new Client({ node: 'http://192.168.10.128:9200' });

/* ================================================================
   JD CDN 图片
   ================================================================ */
const IMG = (id) => `https://img1${id % 5 + 10}.360buyimg.com/n1/s400x400_jfs/t1/${200000 + id * 1000}/12/3456/123456/667a8b9cF0d1e2f3a.jpg`;

const now = new Date().toISOString().slice(0, 19).replace('T', ' ');
const PASS = bcrypt.hashSync('Test1234', 10);

/* ================================================================
   数据
   ================================================================ */
const USERS = [
  { userId: 'u_buyer_001', username: 'buy123', phone: '13800001111', roleType: 1, balance: 100000 },
  { userId: 'u_seller_001', username: 'sell123', phone: '13800002222', roleType: 2, balance: 0 },
  { userId: 'u_seller_002', username: 'sell456', phone: '13800003333', roleType: 2, balance: 0 },
  { userId: 'u_admin_001', username: 'admin', phone: '13800000000', roleType: 3, balance: 0 },
];

const CATEGORIES = [
  { id: 1, name: '居家生活' }, { id: 2, name: '服饰穿搭' },
  { id: 3, name: '数码好物' }, { id: 4, name: '美食饮品' },
  { id: 5, name: '文具书籍' }, { id: 6, name: '运动户外' },
];

const SELLERS = [
  { bizUser: 'u_seller_001', shopName: '冰美精选家居', phone: '13800002222', province: '浙江省', city: '杭州市', district: '西湖区', address: '文三路 138 号' },
  { bizUser: 'u_seller_002', shopName: '数码生活馆', phone: '13800003333', province: '广东省', city: '深圳市', district: '南山区', address: '科技园路 88 号' },
];

const ADDRESSES = [
  { bizUser: 'u_buyer_001', receiver: '张三', phone: '13800001111', province: '北京市', city: '北京市', district: '朝阳区', street: '建国路', detail: '88 号 1201 室', isDefault: 1, label: '家' },
  { bizUser: 'u_buyer_001', receiver: '张三', phone: '13800001111', province: '上海市', city: '上海市', district: '浦东新区', street: '张江路', detail: '科技园区 5 号楼', isDefault: 0, label: '公司' },
];

const PRODUCTS = [
  // 居家生活
  { pid:'prod_001', seller:1, cat:1, name:'手工陶瓷茶杯 — 天青釉', brand:'冰美精选', desc:'景德镇匠人手工拉坯，1280°C 高温烧制。杯壁轻薄如纸，釉色温润如玉。', sold:1280, comment:86,
    skus:[['sku_001','天青/标准(200ml)',12800,50,320],['sku_002','月白/标准(200ml)',12800,30,180],['sku_003','天青/大号(350ml)',16800,20,90]] },
  { pid:'prod_002', seller:1, cat:1, name:'原木台灯 — 黑胡桃木', brand:'冰美精选', desc:'北美黑胡桃木底座，暖白 LED 光源，触摸三档调光。', sold:860, comment:52,
    skus:[['sku_004','黑胡桃木/暖白光',26800,35,200]] },
  { pid:'prod_003', seller:1, cat:1, name:'亚麻编织毯 — 天然本色', brand:'冰美精选', desc:'100% 欧洲亚麻，手工编织。透气吸湿，四季皆宜。', sold:450, comment:31,
    skus:[['sku_005','米白/130×170cm',18800,40,150],['sku_006','浅灰/130×170cm',18800,25,80],['sku_007','米白/170×230cm',25800,15,60]] },
  // 服饰穿搭
  { pid:'prod_004', seller:2, cat:2, name:'棉麻围巾 — 手工植物染', brand:'冰美精选', desc:'新疆长绒棉+亚麻混纺，天然靛蓝植物染色。柔和不刺眼。', sold:2100, comment:145,
    skus:[['sku_008','靛蓝/180×50cm',8900,80,900],['sku_009','茜草红/180×50cm',8900,60,500]] },
  { pid:'prod_005', seller:2, cat:2, name:'手工皮革托特包', brand:'冰美精选×植鞣革', desc:'意大利植鞣革，手工缝线。随使用包浆变色。', sold:620, comment:48,
    skus:[['sku_010','棕色/中号',39800,15,120],['sku_011','黑色/大号',45800,10,80]] },
  { pid:'prod_006', seller:2, cat:2, name:'极简腕表 — 超薄石英', brand:'冰美精选', desc:'日本精工石英机芯，316L不锈钢，蓝宝石玻璃。厚度5.8mm。', sold:340, comment:28,
    skus:[['sku_012','银色/黑色表带',69800,20,90],['sku_013','玫瑰金/棕色表带',76800,12,55]] },
  // 数码好物
  { pid:'prod_007', seller:2, cat:3, name:'真无线降噪耳机 — 40dB ANC', brand:'冰美声学', desc:'混合主动降噪，40dB深度。LDAC高清传输，40h续航。IPX5防水。', sold:3200, comment:256,
    skus:[['sku_014','曜石黑',59900,100,1500],['sku_015','云雾白',59900,80,1200]] },
  { pid:'prod_008', seller:2, cat:3, name:'氮化镓充电器 — 65W 三口', brand:'冰美科技', desc:'GaN第三代半导体，65W大功率，2C1A三口同时充。体积小40%。', sold:1800, comment:132,
    skus:[['sku_016','白色/65W',12900,150,800]] },
  { pid:'prod_009', seller:2, cat:3, name:'便携蓝牙音箱 — 360°环绕声', brand:'冰美声学', desc:'360°全向发声，IP67防尘防水，20h续航。户外露营必备。', sold:950, comment:78,
    skus:[['sku_017','森林绿',29900,45,300],['sku_018','午夜蓝',29900,35,250]] },
  // 美食饮品
  { pid:'prod_010', seller:1, cat:4, name:'明前龙井 — 西湖一级产区', brand:'冰美茶集', desc:'西湖龙井一级保护区，明前采摘。豆香浓郁，回甘悠长。', sold:560, comment:67,
    skus:[['sku_019','50g小罐装',19800,30,200],['sku_020','100g礼盒装',35800,15,80]] },
  { pid:'prod_011', seller:1, cat:4, name:'手冲咖啡豆 — 耶加雪菲 G1', brand:'冰美咖啡', desc:'埃塞俄比亚耶加雪菲G1，水洗处理。柑橘、茉莉花香。', sold:780, comment:92,
    skus:[['sku_021','浅烘/200g',6800,60,350],['sku_022','中烘/200g',6800,45,280]] },
  // 文具书籍
  { pid:'prod_012', seller:1, cat:5, name:'手工绑带笔记本 — A5方格', brand:'冰美文具', desc:'意大利植鞣革封面，可替换内芯。80g无酸纸，不洇墨。', sold:1200, comment:105,
    skus:[['sku_023','棕色/方格内页',12800,70,600],['sku_024','黑色/空白内页',12800,50,400]] },
  { pid:'prod_013', seller:1, cat:5, name:'黄铜制图笔 — 0.5mm', brand:'冰美文具', desc:'全黄铜笔身，德国施密特笔芯。随使用形成包浆。', sold:680, comment:44,
    skus:[['sku_025','黄铜原色/0.5mm',4800,100,400]] },
  // 运动户外
  { pid:'prod_014', seller:2, cat:6, name:'钛合金运动水壶 — 500ml', brand:'冰美户外', desc:'纯钛材质，重量仅120g。双层真空保冷12h。', sold:1500, comment:120,
    skus:[['sku_026','钛原色/500ml',22800,55,700],['sku_027','钛原色/750ml',26800,30,350]] },
  { pid:'prod_015', seller:2, cat:6, name:'天然橡胶瑜伽垫 — 5mm', brand:'冰美运动', desc:'斯里兰卡天然橡胶，PU面层。干湿防滑，可降解环保。', sold:2300, comment:189,
    skus:[['sku_028','深灰/5mm/183×68cm',32800,40,1000],['sku_029','墨绿/5mm/183×68cm',32800,25,650]] },
  { pid:'prod_016', seller:1, cat:1, name:'日式手工玻璃花瓶', brand:'冰美精选', desc:'东京手工吹制玻璃，每一只都独一无二。适合单枝花插放。', sold:380, comment:26,
    skus:[['sku_030','透明/高20cm',9800,25,200]] },
];

/* ================================================================
   MySQL seed
   ================================================================ */
async function seedMySQL() {
  const c = await mysql.createConnection(MYSQL);
  console.log('MySQL connected');

  // truncate
  await c.query('SET FOREIGN_KEY_CHECKS=0');
  const tables = ['address','cart','order_item','orders','pay_order','order_logistics','after_sale','sku','product','favorite','review','points_log','coupon','user_coupon','flash_sale','seller_application','settlement','withdrawal','operation_log','home_config','seller','category','user'];
  for (const t of tables) await c.query('TRUNCATE TABLE `' + t + '`');
  await c.query('SET FOREIGN_KEY_CHECKS=1');
  console.log('Tables truncated');

  // users
  const userIdMap = {};
  for (const u of USERS) {
    const [r] = await c.query(
      'INSERT INTO user (user_id,username,phone,password,role_type,status,balance,register_time,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?)',
      [u.userId,u.username,u.phone,PASS,u.roleType,1,u.balance,now,now,now]);
    userIdMap[u.userId] = r.insertId;
    console.log('  user:', u.username, '(id=' + r.insertId + ')');
  }

  // categories
  for (const cat of CATEGORIES) {
    await c.query('INSERT INTO category (id,name,parent_id,level,sort_order,create_time,update_time) VALUES (?,?,null,1,?,?,?)',
      [cat.id,cat.name,cat.id,now,now]);
  }
  console.log('  categories:', CATEGORIES.length);

  // sellers
  const sellerIdMap = {};
  for (let i = 0; i < SELLERS.length; i++) {
    const s = SELLERS[i];
    const [r] = await c.query(
      'INSERT INTO seller (user_id,shop_name,contact_phone,province,city,district,detail_address,status,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?)',
      [userIdMap[s.bizUser],s.shopName,s.phone,s.province,s.city,s.district,s.address,1,now,now]);
    sellerIdMap[i+1] = r.insertId;
    console.log('  seller:', s.shopName, '(id=' + r.insertId + ')');
  }

  // products + skus
  let totalSkus = 0;
  for (const p of PRODUCTS) {
    const [r] = await c.query(
      'INSERT INTO product (product_id,seller_id,category_id,name,brand,main_image,description,sold_count,comment_count,is_ad,status,publish_time,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,0,1,?,?,?)',
      [p.pid,sellerIdMap[p.seller],p.cat,p.name,p.brand,IMG(totalSkus+1),p.desc,p.sold,p.comment,now,now,now]);
    const dbProductId = r.insertId;
    for (const sku of p.skus) {
      await c.query(
        'INSERT INTO sku (sku_id,product_id,spec,price,stock,image,sold_count,status,version,create_time,update_time) VALUES (?,?,?,?,?,?,?,1,0,?,?)',
        [sku[0],dbProductId,sku[1],sku[2],sku[3],IMG(totalSkus+1),sku[4],now,now]);
      totalSkus++;
    }
    console.log('  product:', p.name.substring(0,20), '+', p.skus.length, 'SKU');
  }
  console.log('  Total:', PRODUCTS.length, 'products,', totalSkus, 'SKUs');

  // addresses
  for (const a of ADDRESSES) {
    await c.query(
      'INSERT INTO address (user_id,receiver,phone,province,city,district,street,detail,is_default,label,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
      [userIdMap[a.bizUser],a.receiver,a.phone,a.province,a.city,a.district,a.street,a.detail,a.isDefault,a.label,now,now]);
  }
  console.log('  addresses:', ADDRESSES.length);

  await c.end();
  console.log('MySQL seed done\n');
}

/* ================================================================
   ES index
   ================================================================ */
async function seedES() {
  // Delete old index
  try { await es.indices.delete({ index: 'products' }); console.log('Old ES index deleted'); } catch(e) {}

  // Create index with IK analyzer
  await es.indices.create({
    index: 'products',
    body: {
      settings: {
        number_of_shards: 1,
        number_of_replicas: 0,
        analysis: { analyzer: { ik_smart_analyzer: { type: 'custom', tokenizer: 'ik_smart' } } }
      },
      mappings: {
        properties: {
          id: { type: 'long' },
          productId: { type: 'keyword' },
          sellerId: { type: 'long' },
          categoryId: { type: 'long' },
          name: { type: 'text', analyzer: 'ik_smart_analyzer', fields: { keyword: { type: 'keyword' } } },
          description: { type: 'text', analyzer: 'ik_smart_analyzer' },
          brand: { type: 'keyword' },
          mainImage: { type: 'keyword', index: false },
          price: { type: 'integer' },
          soldCount: { type: 'integer' },
          commentCount: { type: 'integer' },
          status: { type: 'integer' },
          publishTime: { type: 'date' },
        }
      }
    }
  });
  console.log('ES index created: products (IK smart analyzer)');

  // Read products from MySQL
  const c = await mysql.createConnection(MYSQL);
  const [products] = await c.query(
    'SELECT p.id,p.product_id as productId,p.seller_id as sellerId,p.category_id as categoryId,' +
    'p.name,p.description,p.brand,p.main_image as mainImage,p.sold_count as soldCount,' +
    'p.comment_count as commentCount,p.status,p.publish_time as publishTime,' +
    'MIN(s.price) as price ' +
    'FROM product p LEFT JOIN sku s ON s.product_id=p.id AND s.status=1 ' +
    'WHERE p.status=1 GROUP BY p.id');
  await c.end();

  // Bulk index
  const body = products.flatMap(p => [
    { index: { _index: 'products', _id: String(p.id) } },
    p
  ]);
  const result = await es.bulk({ refresh: true, body });
  console.log(`ES indexed: ${products.length} docs, errors: ${result.errors ? result.items.filter(i=>i.index?.error).length : 0}`);
}

/* ================================================================ */
async function main() {
  await seedMySQL();
  await seedES();
  console.log('\n✅ Done!');
  console.log('  MySQL: buy123/Test1234 (买家)  sell123/Test1234 (卖家)');
  console.log('  ES:    GET /products/_search');
  process.exit(0);
}
main().catch(e => { console.error(e); process.exit(1); });
