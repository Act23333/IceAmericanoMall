/**
 * 订单项「比价 / 失效提示」的**降级态渲染准备**（2026-10-17 B3）
 *
 * ── 为什么现在只有「降级态」这一条路径 ──────────────────────────
 * 后端**未**把比价结果出线格式：`OrderItemVO` 无任何比价字段
 * （ADR-061 §决策 3「B2 落地形态」第 5 条；契约 §十二 12.2【③ 线格式字段待规划】
 * 明示「本批不定义…**前端不得对接**」）。
 * 故本批**不得消费任何比价字段**，但要求把**渲染准备**做好：
 * 报文无比价键时，必须与「**无法比对**」**同款渲染** ——
 *
 *   ✅ 只显快照（`spu_name` / `sku_spec` / `price` / `quantity` / `image`）
 *   ✅ **不报错**（不比价失败不是错误）
 *   ✅ **不缩行**（订单列表/详情的明细条数不受影响 —— 这正是「不缩行」验收条件的渲染侧对应物）
 *   ✅ **不骨架屏卡死**（不比价不发请求、不挂 loading）
 *   ✅ **不回退显示技术 id**（既不显示 `spu.id`/`sku.id`，也不把 32-hex 业务键当商品名）
 *   ❌ **不得**把「缺字段」推断为「已下架 / 已删除」（缺失 ≠ 事实）
 *
 * ── 设计要点：单一函数、单一路径 ────────────────────────────────
 * `readOrderItemCompareState()` 是**唯一**的状态判定入口，`describeCompareState()` 是**唯一**
 * 的文案出口，两条路径（`UNAVAILABLE` 与 `UNCOMPARABLE`）在两者里**合并为同一分支** ——
 * 于是「缺失」与「无法比对」的同款性**由代码结构保证**，而不是靠两处手写保持一致。
 * 待契约定稿并接线后，只需在 `readOrderItemCompareState` 里识别新键，
 * 其余（含降级语义）无需改动。
 *
 * ⚠️ 判定为**防御式**：不假设键名一定叫 `compare_state`（那只是契约 §十二 12.2 的
 *   「**建议**最小形态、仅供参考、未定稿」）。因此采取白名单取值 ——
 *   取值不在已知集合内一律回落 `UNAVAILABLE`（不猜、不报错）。
 *
 * ⚠️ 与后端 `CompareState` 枚举（`trade.domain.service.CompareState`）是**服务端内部枚举**，
 *   「⛔ 不随订单报文出栈」（ADR-061 §决策 3 B2 落地形态 #3）—— 故本文件的类型
 *   是**前端侧的前瞻映射**，不是对现状报文的断言。
 */

import type { OrderItemVO } from '@icedmall/api';

/** 比价态（前端前瞻映射；当前报文恒为 `UNAVAILABLE`）。 */
export type OrderItemCompareState =
  /** **本批实际取值**：报文无任何比价键（含后端未出线格式 / 读模型未启用 / 字段名未知）。 */
  | 'UNAVAILABLE'
  /** SKU 业务键查不到行（物理删除）—— 只显示快照 + 「无法比对」。 */
  | 'UNCOMPARABLE'
  /** 价格与快照不一致。 */
  | 'PRICE_CHANGED'
  /** SPU 下架 / 删除。 */
  | 'OFF_SHELF'
  /** SKU 停售。 */
  | 'DISCONTINUED'
  /** 暂时缺货（`stock=0` 且 `stock_type` 需校验库存）。 */
  | 'OUT_OF_STOCK'
  /** 与快照一致，不提示。 */
  | 'IN_SYNC';

/** 服务端已定义的 `CompareState` 取值白名单（见 ADR-061 §决策 3 与 §十二 12.2 建议形态）。 */
const KNOWN_STATES: ReadonlySet<string> = new Set([
  'UNCOMPARABLE',
  'PRICE_CHANGED',
  'OFF_SHELF',
  'DISCONTINUED',
  'OUT_OF_STOCK',
  'IN_SYNC',
]);

/** 可选比价键（契约**建议**形态，未定稿 —— 只读、不依赖）。 */
const COMPARE_KEY = 'compare_state';

/**
 * 判定订单项的比价态。
 *
 * 报文无该键 / 类型不符 / 取值未知 → 一律 `UNAVAILABLE`（**不抛错、不猜**）。
 */
export function readOrderItemCompareState(item: OrderItemVO): OrderItemCompareState {
  // 防御式读取：契约未定稿，键可能不存在或类型不是字符串
  const raw: unknown = (item as OrderItemVO & Record<string, unknown>)[COMPARE_KEY];
  if (typeof raw !== 'string') return 'UNAVAILABLE';
  return KNOWN_STATES.has(raw) ? (raw as OrderItemCompareState) : 'UNAVAILABLE';
}

/** 比价提示的展示形态。`null` = **不渲染任何提示**（只显快照）。 */
export interface CompareHint {
  /** 提示级别（供样式选择；不参与业务判定）。 */
  tone: 'info' | 'warning';
  /** 提示文案（**绝不包含任何 id**）。 */
  text: string;
}

/**
 * 比价态 → 提示文案（**唯一**文案出口）。
 *
 * ⚠️ `UNAVAILABLE` 与 `UNCOMPARABLE` **共用同一分支** —— 这是「同款渲染」的实现保证。
 *   本批两者均返回 `null`（**不渲染提示** ⇒ 只显快照、不报错、不缩行、不骨架屏、
 *   不回退技术 id），因为后端尚未出线格式时对所有历史订单都挂一条「无法比对」
 *   属于噪声而非信息（缺失 ≠ 事实）。待契约定稿、字段真正出栈后再决定是否给
 *   `UNCOMPARABLE` 加文案 —— 届时改**这一处**即可，`UNAVAILABLE` 会跟随，
 *   同款性不会退化为「两处手写」。
 */
export function describeCompareState(state: OrderItemCompareState): CompareHint | null {
  switch (state) {
    // 「无法比对」与「缺失」—— 同款渲染（同一分支，不可拆开维护）
    case 'UNAVAILABLE':
    case 'UNCOMPARABLE':
      return null;
    case 'PRICE_CHANGED':
      return { tone: 'info', text: '现价与购买时不同' };
    case 'OFF_SHELF':
      return { tone: 'warning', text: '商品已下架' };
    case 'DISCONTINUED':
      return { tone: 'warning', text: '该规格已停售' };
    case 'OUT_OF_STOCK':
      return { tone: 'warning', text: '暂时缺货' };
    case 'IN_SYNC':
      return null;
  }
}

/** 便捷组合：订单项 → 提示（`null` = 只显快照）。 */
export function orderItemCompareHint(item: OrderItemVO): CompareHint | null {
  return describeCompareState(readOrderItemCompareState(item));
}

/**
 * 快照商品名 —— **唯一的商品名取值口径**。
 *
 * `spu_name` 为下单时点快照（非读时回填），正常必有值；仍做空值防御，
 * 且**不回落显示任何 id**（业务键或技术键都不显示），缺失时给中性文案。
 */
export function orderItemDisplayName(item: OrderItemVO): string {
  return item.spu_name && item.spu_name.trim() !== '' ? item.spu_name : '商品信息缺失';
}
