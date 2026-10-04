/**
 * =====================================================================
 * 冰美商城 — 本地开发种子数据脚本（V5.4 重写版）
 * =====================================================================
 *
 * 用法:
 *   npm install            # 仓库根目录，装 mysql2 / bcryptjs（package.json 已声明）
 *   node seed-data.js      # 写入本地 dev 库
 *
 *   连接可用环境变量覆盖（缺省对齐 services 的 application.yml）:
 *     SEED_DB_HOST (默认 192.168.10.128)
 *     SEED_DB_PORT (默认 3306)
 *     SEED_DB_USER (默认 root)
 *     SEED_DB_PASS (默认 root)
 *     SEED_DB_NAME (默认 icedamericano_mall)
 *
 * ---------------------------------------------------------------------
 * ⚠️⚠️  仅限本地开发，禁止流向共享 / 测试 / 预发 / 生产环境  ⚠️⚠️
 * ---------------------------------------------------------------------
 *   1. 本脚本写入 **可登录的弱口令账号**（见下方 DEV_PASSWORD 常量）；
 *   2. 本脚本写入的 PII 密文使用 **内置开发密钥**（下方 DEV_AES_KEY /
 *      DEV_HMAC_KEY），与 ia-common `CryptoKeyConfig.DEV_AES_KEY /
 *      DEV_HMAC_KEY` 逐字节一致 —— 该密钥随源码公开，等于"没有加密"；
 *   3. 任何非本地环境请改用运维注入的强口令账号 + KMS 密钥
 *      （IA_CRYPTO_AES_KEY / IA_CRYPTO_HMAC_KEY），并删除本脚本产物。
 *
 * ---------------------------------------------------------------------
 * 密钥 / 算法口径（与 Java 实现 1:1 对齐，详见文件末尾"口径对照表"）
 * ---------------------------------------------------------------------
 *   AES 数据密钥    base64("ice-americano-dev-aes-key-32byte")  —— CryptoKeyConfig.DEV_AES_KEY
 *   HMAC 索引密钥   base64("ice-americano-dev-hmac-index-key")  —— CryptoKeyConfig.DEV_HMAC_KEY
 *   phone_cipher    AES-256-GCM；密文串 = base64( iv[12] || ciphertext || tag[16] )
 *                   （AesGcmUtil.encrypt：12 字节随机 IV + 128-bit tag，GCM 无 AAD）
 *   phone_hash      HMAC-SHA256(明文, HMAC_KEY) 的 **小写十六进制**（64 字符）
 *                   （BlindIndexUtil.hmac：HexFormat.formatHex）
 *   phone_mask      DesensitizeUtil.maskPhone：前 3 位 + "****" + 后 4 位
 *   credential      BCrypt（Spring Security BCryptPasswordEncoder，强度 10）
 *                   注：bcryptjs v3.0.3 固定输出 $2b$10$ 前缀（minor 参数已废弃）；
 *                   Spring Security 的 BCryptPattern = \A\$2(a|y|b)?\$\d\d\$[./0-9A-Za-z]{53}，
 *                   显式接受 a/y/b 三种版本标记，故 bcryptjs 生成的哈希可直接被
 *                   PasswordVerifierAdapter（BCryptPasswordEncoder.matches）校验通过。
 *
 * 密码常量声明：
 *   所有种子账号统一密码 = DEV_PASSWORD（下方常量，= "Dev123456"）。
 *   满足 RegisterReq 口令正则 ^(?![0-9]+$)(?![a-zA-Z]+$)[0-9a-zA-Z!@#$%^&*]{6,20}$
 *
 * ---------------------------------------------------------------------
 * 幂等策略（可重复执行，结果确定）
 * ---------------------------------------------------------------------
 *   单连接 + 单事务包裹：
 *     Phase 1  DELETE  —— 按固定业务键（user_id / product_id / sku_id …）
 *                          以「子表 → 父表」顺序删除本脚本自有数据（不含无关数据）。
 *                          依赖链守卫：先删 orders/order_item（RESTRICT 指向
 *                          user/seller/sku），否则旧订单会阻塞商家/商品删除。
 *     Phase 2  INSERT  —— 重建；user 自增主键回收后 seller.id 显式取 seller.user_id
 *                          同一值（见"主键口径"注释），保证 product.seller_id 口径。
 *   参考数据（category / sys_role / sys_permission / sys_role_permission）不删，
 *     走 INSERT ... ON DUPLICATE KEY UPDATE（与 Initialize.sql §九 同风格），
 *     避免因他人数据引用而触发 RESTRICT。
 *
 * ---------------------------------------------------------------------
 * 主键口径（V5.4 Phase A 的关键约束）
 * ---------------------------------------------------------------------
 *   下游表（product / orders / coupon / settlement / knowledge_base）的
 *   seller_id 在 Phase B 前维持「商家 user_id」口径（见 03-Domain-Model §3.3.1、
 *   04-Data-Model §3.3）：item-service 建品时 seller_id = UserContext.getUserId()。
 *   但 product.seller_id 上的外键 `fk_product_seller` 要求该值同时存在于 seller.id。
 *   故本脚本 INSERT seller 时 **显式指定 id = 该商家的 user.id**，使
 *   seller.id === seller.user_id —— 同时满足外键与 user_id 口径，且
 *   `GET /api/shop/{sellerId}`（sellerId = user_id）→ 默认店铺解析成立。
 *
 * 表结构权威：database/Initialize.sql（全量快照，40 表终态）。
 * =====================================================================
 */

'use strict';

const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const mysql = require('mysql2/promise');

/* =====================================================================
   一、连接配置（可用环境变量覆盖）
   ===================================================================== */
const DB = {
  host: process.env.SEED_DB_HOST || '192.168.10.128',
  port: Number(process.env.SEED_DB_PORT || 3306),
  user: process.env.SEED_DB_USER || 'root',
  password: process.env.SEED_DB_PASS || 'root',
  database: process.env.SEED_DB_NAME || 'icedamericano_mall',
  charset: 'utf8mb4',
};

/* =====================================================================
   二、加密口径常量（与 CryptoKeyConfig.DEV_* 逐字节一致，勿改）
   ===================================================================== */
const DEV_AES_KEY_B64 = 'aWNlLWFtZXJpY2Fuby1kZXYtYWVzLWtleS0zMmJ5dGU=';  // base64(32B)
const DEV_HMAC_KEY_B64 = 'aWNlLWFtZXJpY2Fuby1kZXYtaG1hYy1pbmRleC1rZXk='; // base64(32B)

const AES_KEY = Buffer.from(DEV_AES_KEY_B64, 'base64');
const HMAC_KEY = Buffer.from(DEV_HMAC_KEY_B64, 'base64');
const AES_IV_BYTES = 12;   // AesGcmUtil: new byte[12]
const GCM_TAG_BYTES = 16;  // GCMParameterSpec(128, iv)

/** BCrypt 强度（与 SecurityConfig 的 new BCryptPasswordEncoder() 缺省 10 一致）。 */
const BCRYPT_ROUNDS = 10;

/** 种子账号统一密码（仅本地开发；满足注册口令正则） */
const DEV_PASSWORD = 'Dev123456';

/* =====================================================================
   三、PII 三字段工具（Node 内置 crypto 实现 Java 口径）
   ===================================================================== */

/**
 * AES-256-GCM 加密 —— 对齐 AesGcmUtil.encrypt。
 * 输出 base64( iv[12] || ciphertext || tag[16] )，其中 tag 为 128-bit。
 * @param {string} plain 明文
 * @returns {string} Base64 密文
 */
function aesEncrypt(plain) {
  const iv = crypto.randomBytes(AES_IV_BYTES);
  const cipher = crypto.createCipheriv('aes-256-gcm', AES_KEY, iv);
  const body = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, body, tag]).toString('base64');
}

/**
 * HMAC-SHA256 盲索引 —— 对齐 BlindIndexUtil.hmac（小写十六进制）。
 * @param {string} plain 明文
 * @returns {string} 64 字符小写 hex
 */
function hmacIndex(plain) {
  return crypto.createHmac('sha256', HMAC_KEY).update(plain, 'utf8').digest('hex');
}

/** 手机号脱敏 —— 对齐 DesensitizeUtil.maskPhone：前 3 + "****" + 后 4。 */
function maskPhone(phone) {
  if (!phone || phone.length < 11) return phone;
  return phone.slice(0, 3) + '****' + phone.slice(7);
}

/** 邮箱脱敏 —— 对齐 DesensitizeUtil.maskEmail。 */
function maskEmail(email) {
  if (!email || !email.includes('@')) return email;
  const [name, domain] = email.split('@');
  return name.length <= 2 ? `***@${domain}` : `${name.slice(0, 2)}***@${domain}`;
}

/** 通用脱敏 —— 对齐 DesensitizeUtil.mask（按手机号/邮箱自动选择）。 */
function maskIdentifier(value) {
  if (!value) return value;
  if (/^1[3-9]\d{9}$/.test(value)) return maskPhone(value);
  if (value.includes('@')) return maskEmail(value);
  return value;
}

/**
 * 构造 user_auth 行（敏感标识：PHONE / EMAIL）。
 * 三字段：identifier_cipher / identifier_hash / identifier_mask。
 */
function sensitiveAuth(userId, identityType, raw, credential) {
  return {
    userId,
    identityType,
    identifierPlain: null,
    identifierCipher: aesEncrypt(raw),
    identifierHash: hmacIndex(raw),
    identifierMask: maskIdentifier(raw),
    credential,
  };
}

/** 构造 user_auth 行（非敏感标识：USERNAME，明文入库）。 */
function plainAuth(userId, identityType, plain, credential) {
  return {
    userId,
    identityType,
    identifierPlain: plain,
    identifierCipher: null,
    identifierHash: null,
    identifierMask: null,
    credential,
  };
}

/* =====================================================================
   四、种子数据定义
   ===================================================================== */

/** 图片：picsum.photos，同一 seed 稳定返回同一张图（seed 仅字母数字）。 */
const mainImg = (seed) => `https://picsum.photos/seed/${seed}/400/400`;
const gallery = (seed) => JSON.stringify([
  `https://picsum.photos/seed/${seed}a/800/800`,
  `https://picsum.photos/seed/${seed}b/800/800`,
  `https://picsum.photos/seed/${seed}c/800/800`,
]);
const skuImg = (seed) => `https://picsum.photos/seed/${seed}/400/400`;

/** 一级类目（ID 固定，走 upsert，不删除）。 */
const CATEGORIES = [
  { id: 1, name: '居家生活', sortOrder: 1 },
  { id: 2, name: '服饰穿搭', sortOrder: 2 },
  { id: 3, name: '数码好物', sortOrder: 3 },
  { id: 4, name: '美食饮品', sortOrder: 4 },
  { id: 5, name: '文具书籍', sortOrder: 5 },
  { id: 6, name: '运动户外', sortOrder: 6 },
];

/**
 * 用户（4 角色全覆盖：1 管理员 + 2 买家 + 2 商家）。
 * roleType 采用**对外派生口径**（RoleTypeDerivation：0=普通用户 / 1=商家 / 2=管理员）。
 * 注意：user.role_type 列 V5.2 起已 deprecated（代码不读写，权限唯一权威为
 * sys_user_role 五表）；此处写入仅为终态快照一致性 + 兼容旧读路径，权限实际由
 * 下方 sys_user_role 授予。
 */
const USERS = [
  { userId: 'admin-001',    username: 'admin',   phone: '13900000000', email: null,                  roleType: 2, status: 1, nickname: '平台管理员' },
  { userId: 'u_buyer_001',  username: 'buy123',  phone: '13800001111', email: 'buyer1@dev.local',    roleType: 0, status: 1, nickname: '张三' },
  { userId: 'u_buyer_002',  username: 'buy456',  phone: '13800002222', email: 'buyer2@dev.local',    roleType: 0, status: 1, nickname: '李四' },
  { userId: 'u_seller_001', username: 'sell123', phone: '13800003333', email: null,                  roleType: 1, status: 1, nickname: '冰美家居旗舰' },
  { userId: 'u_seller_002', username: 'sell456', phone: '13800004444', email: null,                  roleType: 1, status: 1, nickname: '数码生活馆' },
];

/** 钱包余额（分）——user.balance 已冻结，余额归 wallet_account（V5.3）。 */
const WALLET = {
  'admin-001': 0,
  'u_buyer_001': 100000,  // 1000.00 元
  'u_buyer_002': 50000,   // 500.00 元
  'u_seller_001': 0,
  'u_seller_002': 0,
};

/** sys_user_role 授予：买家→ROLE_USER(1)；商家→ROLE_SELLER(3)+ROLE_USER(1)；管理员→ROLE_ADMIN(4)。 */
const ROLE_USER_ID = 1;
const ROLE_SELLER_ID = 3;
const ROLE_ADMIN_ID = 4;

/**
 * 商家（seller 5 列终态）+ 默认店铺（shop）。
 * merchantUserId → seller.user_id（= seller.id，见文件头"主键口径"）。
 */
const MERCHANTS = [
  {
    merchantUserId: 'u_seller_001',
    shop: { shopName: '冰美精选家居', contactPhone: '13800003333', province: '浙江省', city: '杭州市', district: '西湖区', detailAddress: '文三路 138 号' },
  },
  {
    merchantUserId: 'u_seller_002',
    shop: { shopName: '数码生活馆', contactPhone: '13800004444', province: '广东省', city: '深圳市', district: '南山区', detailAddress: '科技园路 88 号' },
  },
];

/** 地址（买家）。 */
const ADDRESSES = [
  { buyerUserId: 'u_buyer_001', receiver: '张三', phone: '13800001111', province: '北京市', city: '北京市', district: '朝阳区', street: '建国路', detail: '88 号 1201 室', isDefault: 1, label: '家' },
  { buyerUserId: 'u_buyer_001', receiver: '张三', phone: '13800001111', province: '上海市', city: '上海市', district: '浦东新区', street: '张江路', detail: '科技园 5 号楼', isDefault: 0, label: '公司' },
  { buyerUserId: 'u_buyer_002', receiver: '李四', phone: '13800002222', province: '广东省', city: '广州市', district: '天河区', street: '天河路', detail: '300 号 808 室', isDefault: 1, label: '家' },
];

/**
 * 商品 + SKU：每商家 3 款在售商品（status=1），价格单位为分，库存非负。
 * sellerRef 指向 MERCHANTS[].merchantUserId。
 */
const PRODUCTS = [
  // ---- 商家 1（冰美精选家居）----
  {
    productId: 'prod_seed_001', sellerRef: 'u_seller_001', categoryId: 1,
    name: '手工陶瓷茶杯 — 天青釉', brand: '冰美精选', soldCount: 1280, commentCount: 86,
    description: '景德镇匠人手工拉坯，1280°C 高温烧制。杯壁轻薄如纸，釉色温润如玉。',
    skus: [
      { skuId: 'sku_seed_001', spec: '天青 / 标准 (200ml)', price: 12800, originalPrice: 15800, stock: 50, soldCount: 320 },
      { skuId: 'sku_seed_002', spec: '月白 / 标准 (200ml)', price: 12800, originalPrice: 15800, stock: 30, soldCount: 180 },
      { skuId: 'sku_seed_003', spec: '天青 / 大号 (350ml)', price: 16800, originalPrice: 19800, stock: 20, soldCount: 90 },
    ],
  },
  {
    productId: 'prod_seed_002', sellerRef: 'u_seller_001', categoryId: 1,
    name: '原木台灯 — 黑胡桃木', brand: '冰美精选', soldCount: 860, commentCount: 52,
    description: '北美黑胡桃木底座，暖白 LED 光源，触摸三档调光。',
    skus: [
      { skuId: 'sku_seed_004', spec: '黑胡桃木 / 暖白光', price: 26800, originalPrice: 29800, stock: 35, soldCount: 200 },
    ],
  },
  {
    productId: 'prod_seed_003', sellerRef: 'u_seller_001', categoryId: 4,
    name: '手冲咖啡豆 — 耶加雪菲 G1', brand: '冰美咖啡', soldCount: 780, commentCount: 92,
    description: '埃塞俄比亚耶加雪菲 G1，水洗处理。柑橘与茉莉花香。',
    skus: [
      { skuId: 'sku_seed_005', spec: '浅烘 / 200g', price: 6800, originalPrice: 8800, stock: 60, soldCount: 350 },
      { skuId: 'sku_seed_006', spec: '中烘 / 200g', price: 6800, originalPrice: 8800, stock: 45, soldCount: 280 },
    ],
  },
  // ---- 商家 2（数码生活馆）----
  {
    productId: 'prod_seed_004', sellerRef: 'u_seller_002', categoryId: 3,
    name: '真无线降噪耳机 — 40dB ANC', brand: '冰美声学', soldCount: 3200, commentCount: 256,
    description: '混合主动降噪 40dB，LDAC 高清传输，40h 续航。',
    skus: [
      { skuId: 'sku_seed_007', spec: '曜石黑', price: 59900, originalPrice: 69900, stock: 100, soldCount: 1500 },
      { skuId: 'sku_seed_008', spec: '云雾白', price: 59900, originalPrice: 69900, stock: 80, soldCount: 1200 },
    ],
  },
  {
    productId: 'prod_seed_005', sellerRef: 'u_seller_002', categoryId: 2,
    name: '棉麻围巾 — 手工植物染', brand: '冰美精选', soldCount: 2100, commentCount: 145,
    description: '新疆长绒棉 + 亚麻混纺，天然靛蓝植物染色。',
    skus: [
      { skuId: 'sku_seed_009', spec: '靛蓝 / 180×50cm', price: 8900, originalPrice: 12800, stock: 80, soldCount: 900 },
      { skuId: 'sku_seed_010', spec: '茜草红 / 180×50cm', price: 8900, originalPrice: 12800, stock: 60, soldCount: 500 },
    ],
  },
  {
    productId: 'prod_seed_006', sellerRef: 'u_seller_002', categoryId: 6,
    name: '钛合金运动水壶 — 500ml', brand: '冰美户外', soldCount: 1500, commentCount: 120,
    description: '纯钛材质 120g，双层真空保冷 12h。',
    skus: [
      { skuId: 'sku_seed_011', spec: '钛原色 / 500ml', price: 22800, originalPrice: 26800, stock: 55, soldCount: 700 },
      { skuId: 'sku_seed_012', spec: '钛原色 / 750ml', price: 26800, originalPrice: 30800, stock: 30, soldCount: 350 },
    ],
  },
];

const SEED_USER_IDS = USERS.map((u) => u.userId);
const SEED_PRODUCT_IDS = PRODUCTS.map((p) => p.productId);
const SEED_SKU_IDS = PRODUCTS.flatMap((p) => p.skus.map((s) => s.skuId));
const SEED_SHOP_NAMES = MERCHANTS.map((m) => m.shop.shopName);

/* =====================================================================
   五、执行主流程（可注入 conn，便于静态验证 / 测试）
   ===================================================================== */

/** 空数组跳过（否则 mysql2 会生成非法的 `IN ()`）。 */
async function deleteIn(conn, sqlPrefix, ids) {
  if (!ids.length) return;
  await conn.query(`${sqlPrefix} (?)`, [ids]);
}

/**
 * 写入种子数据（单事务；幂等）。调用方负责连接生命周期。
 * @param {import('mysql2/promise').Connection} conn 已建立的连接
 */
async function seed(conn) {
  const now = new Date().toISOString().slice(0, 19).replace('T', ' ');

  await conn.beginTransaction();
  try {
    // ========== Phase 0：定位既有 seed 主键（幂等删除用） ==========
    const userIds = await selectIds(conn, 'SELECT id FROM `user` WHERE user_id IN (?)', [SEED_USER_IDS]);
    const sellerIds = userIds.length
      ? await selectIds(conn, 'SELECT id FROM `seller` WHERE user_id IN (?)', [userIds])
      : [];
    const productIds = await selectIds(conn, 'SELECT id FROM `product` WHERE product_id IN (?)', [SEED_PRODUCT_IDS]);
    const skuIds = productIds.length
      ? await selectIds(conn, 'SELECT id FROM `sku` WHERE product_id IN (?)', [productIds])
      : [];
    const orderIds = (userIds.length || sellerIds.length)
      ? await selectIds(conn,
        'SELECT id FROM `orders` WHERE user_id IN (?) OR seller_id IN (?)',
        [userIds.length ? userIds : [0], sellerIds.length ? sellerIds : [0]])
      : [];

    // ========== Phase 1：按业务键删除（子 → 父） ==========
    console.log('清空本脚本自有的种子数据（子 → 父）...');
    await deleteIn(conn, 'DELETE FROM `order_item` WHERE order_id IN', orderIds);
    await deleteIn(conn, 'DELETE FROM `order_logistics` WHERE order_id IN', orderIds);
    await deleteIn(conn, 'DELETE FROM `orders` WHERE id IN', orderIds);
    await deleteIn(conn, 'DELETE FROM `cart` WHERE user_id IN', userIds);
    await deleteIn(conn, 'DELETE FROM `sku` WHERE id IN', skuIds);
    await deleteIn(conn, 'DELETE FROM `product` WHERE id IN', productIds);
    await deleteIn(conn, 'DELETE FROM `shop` WHERE merchant_id IN', sellerIds);
    await deleteIn(conn, 'DELETE FROM `seller` WHERE id IN', sellerIds);
    await deleteIn(conn, 'DELETE FROM `address` WHERE user_id IN', userIds);
    await deleteIn(conn, 'DELETE FROM `wallet_account` WHERE user_id IN', userIds);
    await deleteIn(conn, 'DELETE FROM `user_auth` WHERE user_id IN', userIds);
    await deleteIn(conn, 'DELETE FROM `sys_user_role` WHERE user_id IN', userIds);
    await deleteIn(conn, 'DELETE FROM `user` WHERE id IN', userIds);

    // ========== Phase 2a：参考数据 upsert（不删除） ==========
    for (const c of CATEGORIES) {
      await conn.query(
        `INSERT INTO \`category\` (id, name, parent_id, level, sort_order, create_time, update_time)
         VALUES (?, ?, NULL, 1, ?, ?, ?)
         ON DUPLICATE KEY UPDATE name = VALUES(name), sort_order = VALUES(sort_order), update_time = VALUES(update_time)`,
        [c.id, c.name, c.sortOrder, now, now]);
    }
    console.log(`  类目 upsert: ${CATEGORIES.length}`);

    // ========== Phase 2b：用户（资料 + 三字段 PII） ==========
    const bcryptHash = bcrypt.hashSync(DEV_PASSWORD, BCRYPT_ROUNDS); // bcryptjs v3 → $2b$10$（Spring 接受）
    const userIdMap = {}; // businessUserId -> user.id（技术主键）
    const authRows = [];  // 收集 user_auth 行，待 user 落库后批量写入

    for (const u of USERS) {
      const [result] = await conn.query(
        `INSERT INTO \`user\`
           (user_id, username, phone_cipher, phone_hash, phone_mask,
            email_cipher, email_hash, email_mask,
            avatar, gender, member_level, register_time, status, role_type, version,
            create_time, update_time)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 0, 'NORMAL', ?, ?, ?, 0, ?, ?)`,
        [
          u.userId, u.username,
          aesEncrypt(u.phone), hmacIndex(u.phone), maskPhone(u.phone),
          u.email ? aesEncrypt(u.email) : null,
          u.email ? hmacIndex(u.email) : null,
          u.email ? maskEmail(u.email) : null,
          now, u.status, u.roleType, now, now,
        ]);
      const uid = result.insertId;
      userIdMap[u.userId] = uid;

      // user_auth：USERNAME（明文）+ PHONE（敏感三字段）；有邮箱再补 EMAIL 行
      authRows.push(plainAuth(uid, 'USERNAME', u.username, bcryptHash));
      authRows.push(sensitiveAuth(uid, 'PHONE', u.phone, bcryptHash));
      if (u.email) authRows.push(sensitiveAuth(uid, 'EMAIL', u.email, bcryptHash));

      // 钱包账户（余额单位：分）
      await conn.query(
        `INSERT INTO \`wallet_account\` (user_id, balance, create_time, update_time) VALUES (?, ?, ?, ?)`,
        [uid, WALLET[u.userId] || 0, now, now]);
    }
    console.log(`  user: ${USERS.length}（密码统一 ${DEV_PASSWORD}）`);
    console.log(`  wallet_account: ${USERS.length}`);

    // ========== Phase 2c：user_auth 凭证 ==========
    for (const a of authRows) {
      await conn.query(
        `INSERT INTO \`user_auth\`
           (user_id, identity_type, identifier_plain, identifier_cipher, identifier_hash,
            identifier_mask, union_id, credential, status, last_login_time, create_time, update_time)
         VALUES (?, ?, ?, ?, ?, ?, NULL, ?, 1, NULL, ?, ?)`,
        [a.userId, a.identityType, a.identifierPlain, a.identifierCipher, a.identifierHash,
          a.identifierMask, a.credential, now, now]);
    }
    console.log(`  user_auth: ${authRows.length} 行（USERNAME 明文 + PHONE/EMAIL 三字段）`);

    // ========== Phase 2d：角色授予（sys_user_role） ==========
    const roleGrants = [];
    for (const u of USERS) {
      const uid = userIdMap[u.userId];
      if (u.roleType === 2) roleGrants.push([uid, ROLE_ADMIN_ID]);
      else if (u.roleType === 1) roleGrants.push([uid, ROLE_SELLER_ID], [uid, ROLE_USER_ID]);
      else roleGrants.push([uid, ROLE_USER_ID]);
    }
    for (const [uid, roleId] of roleGrants) {
      await conn.query('INSERT INTO `sys_user_role` (user_id, role_id) VALUES (?, ?)', [uid, roleId]);
    }
    console.log(`  sys_user_role: ${roleGrants.length} 行`);

    // ========== Phase 2e：商家 + 默认店铺 ==========
    const sellerIdByUser = {}; // merchantUserId -> seller.id
    for (const m of MERCHANTS) {
      const uid = userIdMap[m.merchantUserId];
      // 显式 id = user.id：满足 fk_product_seller 与 user_id 口径（见文件头"主键口径"）
      await conn.query(
        `INSERT INTO \`seller\` (id, user_id, status, create_time, update_time) VALUES (?, ?, 1, ?, ?)`,
        [uid, uid, now, now]);
      sellerIdByUser[m.merchantUserId] = uid;

      await conn.query(
        `INSERT INTO \`shop\`
           (merchant_id, shop_name, shop_logo, contact_phone, province, city, district, detail_address,
            status, create_time, update_time)
         VALUES (?, ?, NULL, ?, ?, ?, ?, ?, 1, ?, ?)`,
        [uid, m.shop.shopName, m.shop.contactPhone, m.shop.province, m.shop.city,
          m.shop.district, m.shop.detailAddress, now, now]);
    }
    console.log(`  seller: ${MERCHANTS.length}, shop: ${MERCHANTS.length}（status=1 正常）`);

    // ========== Phase 2f：商品 + SKU ==========
    let skuTotal = 0;
    for (const p of PRODUCTS) {
      const sellerId = sellerIdByUser[p.sellerRef];
      const [result] = await conn.query(
        `INSERT INTO \`product\`
           (product_id, seller_id, category_id, name, main_image, images, description, brand,
            sold_count, comment_count, is_ad, status, publish_time, create_time, update_time)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?, ?)`,
        [p.productId, sellerId, p.categoryId, p.name, mainImg(p.productId), gallery(p.productId),
          p.description, p.brand, p.soldCount, p.commentCount, now, now, now]);
      const productPk = result.insertId;

      for (const s of p.skus) {
        await conn.query(
          `INSERT INTO \`sku\`
             (sku_id, product_id, spec, price, original_price, stock, image, sold_count, status, version,
              create_time, update_time)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?)`,
          [s.skuId, productPk, s.spec, s.price, s.originalPrice, s.stock, skuImg(s.skuId), s.soldCount, now, now]);
        skuTotal++;
      }
    }
    console.log(`  product: ${PRODUCTS.length}, sku: ${skuTotal}（status=1 在售）`);

    // ========== Phase 2g：地址 ==========
    for (const a of ADDRESSES) {
      await conn.query(
        `INSERT INTO \`address\`
           (user_id, receiver, phone, province, city, district, street, detail, is_default, label,
            create_time, update_time)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [userIdMap[a.buyerUserId], a.receiver, a.phone, a.province, a.city, a.district,
          a.street, a.detail, a.isDefault, a.label, now, now]);
    }
    console.log(`  address: ${ADDRESSES.length}`);

    await conn.commit();
  } catch (err) {
    await conn.rollback();
    throw err;
  }
}

/** 执行 SELECT 并抽取单列值。 */
async function selectIds(conn, sql, params) {
  const [rows] = await conn.query(sql, params);
  return rows.map((r) => Object.values(r)[0]);
}

/* =====================================================================
   六、入口
   ===================================================================== */
async function main() {
  const conn = await mysql.createConnection(DB);
  console.log(`已连接 MySQL: ${DB.host}:${DB.port}/${DB.database}`);
  try {
    await seed(conn);
    console.log('\n种子数据写入完成（事务已提交）。');
    console.log('本地开发登录账号（密码统一 Dev123456）:');
    console.log('  管理员 admin   / 13900000000');
    console.log('  买家   buy123  / 13800001111');
    console.log('  买家   buy456  / 13800002222');
    console.log('  商家   sell123 / 13800003333（店铺：冰美精选家居）');
    console.log('  商家   sell456 / 13800004444（店铺：数码生活馆）');
  } finally {
    await conn.end();
  }
}

if (require.main === module) {
  main().catch((e) => {
    console.error('种子写入失败（已回滚）:', e);
    process.exit(1);
  });
}

module.exports = {
  seed,
  DEV_PASSWORD,
  // 导出工具与数据，便于离线静态验证（不连库）
  aesEncrypt,
  hmacIndex,
  maskPhone,
  maskEmail,
  maskIdentifier,
  USERS,
  MERCHANTS,
  PRODUCTS,
  CATEGORIES,
  ADDRESSES,
  WALLET,
  SEED_USER_IDS,
  SEED_PRODUCT_IDS,
  SEED_SKU_IDS,
  SEED_SHOP_NAMES,
  DEV_AES_KEY_B64,
  DEV_HMAC_KEY_B64,
};
