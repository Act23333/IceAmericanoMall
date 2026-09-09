/**
 * 小程序模拟数据 + CDN 图片 (V3)
 *
 * 微信 <Image> 不支持 SVG data URI，改用真实 CDN。
 * 所有图片来自 picsum.photos — 全球 CDN，稳定快速。
 *
 * 开发模式: 微信开发者工具 → 本地设置 → 勾选「不校验合法域名」
 */

// ==================== CDN 图片 ====================

const CDN = 'https://picsum.photos';

/** 商品卡片图 — 用 product.id 作为 seed，同一商品始终同一张图 */
export function productImage(id: number, size = 400): string {
  return `${CDN}/seed/p${id}/${size}/${size}`;
}

/** Banner 横幅图 */
export function bannerImage(index: number): string {
  return `${CDN}/seed/banner${index}/750/300`;
}

/** 商品详情大图 */
export function detailImage(id: number): string {
  return `${CDN}/seed/detail${id}/800/800`;
}

/** 头像 */
export function avatarImage(seed: string): string {
  const s = typeof seed === 'string' ? seed.replace(/[^a-zA-Z0-9]/g, '').slice(0, 8) || 'user' : 'user';
  return `${CDN}/seed/av${s}/200/200`;
}

/** 购物车缩略图 */
export function thumbImage(id: number): string {
  return `${CDN}/seed/thumb${id}/120/120`;
}

/** 秒杀商品图 */
export function flashImage(index: number): string {
  return `${CDN}/seed/flash${index}/160/160`;
}

/** 店铺 Logo */
export function shopLogoImg(name: string): string {
  const s = String(name || 'shop').replace(/[^a-zA-Z0-9]/g, '').slice(0, 8) || 'shop';
  return `${CDN}/seed/shop${s}/200/200`;
}

// ==================== 模拟数据 ====================

export const MOCK_CATEGORIES = [
  { id: 1, name: '居家生活', icon: '🏠', color: '#F5F0EB' },
  { id: 2, name: '服饰穿搭', icon: '👗', color: '#F0EBF5' },
  { id: 3, name: '数码好物', icon: '📱', color: '#EBF0F5' },
  { id: 4, name: '美食饮品', icon: '🍵', color: '#F5F3EB' },
  { id: 5, name: '文具书籍', icon: '📚', color: '#EBF5F0' },
  { id: 6, name: '运动户外', icon: '🏃', color: '#F5EBEB' },
];

export const MOCK_BANNERS = [
  { id: 1, title: '冰美精选 — 东方自然主义', image: bannerImage(1), link: '/pages/marketplace/index' },
  { id: 2, title: '新品首发 — 手工陶瓷系列', image: bannerImage(2), link: '/pages/marketplace/index' },
  { id: 3, title: '限时秒杀 — 每日10点开抢', image: bannerImage(3), link: '/pages/flash/index' },
];

export const MOCK_PRODUCTS = [
  { id: 1, name: '手工陶瓷茶杯',  price: 12800, soldCount: 1280 },
  { id: 2, name: '原木台灯',      price: 26800, soldCount: 860 },
  { id: 3, name: '亚麻编织毯',    price: 18800, soldCount: 450 },
  { id: 4, name: '棉麻围巾',      price: 8900,  soldCount: 2100 },
  { id: 5, name: '手工皮革托特包', price: 39800, soldCount: 620 },
  { id: 6, name: '真无线降噪耳机', price: 59900, soldCount: 3200 },
  { id: 7, name: '氮化镓充电器',  price: 12900, soldCount: 1800 },
  { id: 8, name: '明前龙井',      price: 19800, soldCount: 560 },
];
