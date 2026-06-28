/**
 * 格式化工具函数
 *
 * 金额规则：后端用 INT（分），前端展示用 "12.50" 元
 */

/**
 * 分 → 元（字符串，保留两位小数）
 * formatPrice(1250) → "12.50"
 * formatPrice(100)  → "1.00"
 */
export function formatPrice(cents: number): string {
  return (cents / 100).toFixed(2);
}

/**
 * 分 → 元（数字）
 * priceToYuan(1250) → 12.5
 */
export function priceToYuan(cents: number): number {
  return cents / 100;
}

/**
 * 元 → 分（整数）
 * yuanToCents("12.50") → 1250
 */
export function yuanToCents(yuan: number | string): number {
  return Math.round(Number(yuan) * 100);
}

/**
 * 千分位格式化
 * formatNumber(12345) → "12,345"
 */
export function formatNumber(n: number): string {
  return n.toLocaleString('zh-CN');
}

/**
 * 相对时间
 * timeAgo("2026-06-28T10:00:00") → "3分钟前" / "2小时前" / "3天前"
 */
export function timeAgo(dateStr: string): string {
  const now = Date.now();
  const then = new Date(dateStr).getTime();
  const diff = now - then;

  const minutes = Math.floor(diff / 60_000);
  const hours = Math.floor(diff / 3_600_000);
  const days = Math.floor(diff / 86_400_000);

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 30) return `${days}天前`;
  return new Date(dateStr).toLocaleDateString('zh-CN');
}
