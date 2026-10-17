/**
 * SPU 详情回链的**唯一构造出口**（2026-10-18 批次 B8）
 *
 * ── 为什么需要它 ────────────────────────────────────────────────
 * B3 期因「`VO` 只带 SPU **业务键**、而 P2 详情路径变量是**技术键** `Long`」，
 * 所有商品页深链都被移除（当时照抄只会渲染 `/product/undefined`）。
 * 2026-10-18 后端（`265543d6`）把路径变量改为**业务键 32-hex**
 * （契约 §P2 / §1.9，ADR-062 §决策 3）⇒ 回链能力恢复，收藏 / 足迹 / 秒杀 / 订单项 /
 * 购物车都能跳 `/spu/{spu_id}`。
 *
 * ── 但「恢复」不等于「随便拼」 ──────────────────────────────────
 * 回填是 fail-open 的：`FavoriteVO.spu_id` / `CartItemVO.spu_id` 在商品/SKU 已删除、
 * 读模型降级时可能缺失。直接模板拼接会得到字面量 `/spu/undefined`（B3 已踩过这个坑）。
 * **因此本文件是唯一出口**：缺失 → 返回 `null`，调用方据此**不渲染链接**（而非渲染空链接）。
 *
 * ── 形态判据的取舍 ──────────────────────────────────────────────
 * 这里只判「非空字符串」，**不**做 32-hex 形态断言：前端的职责是「有键就跳、
 * 无键不跳」；键**形态**是否合法由后端入口 `IdGenerator#requireWire` 把关
 * （非法即 400 fail-fast，而不是前端静默把用户挡在页外）。若前端也复制一份 hex 正则，
 * 一旦后端扩展形态（如未来兼容大写）就会变成「链接整体消失」的静默故障。
 */

/** SPU 详情路由段（App Router 目录名 `apps/web/app/spu/[spuId]`）。 */
export const SPU_DETAIL_SEGMENT = 'spu';

/**
 * 是否具备可寻址的 SPU 业务键。
 *
 * @param spuId SPU 业务键（`spu_id` / `spuId`，32-hex）；回填缺失时为 `null`/`undefined`
 */
export function hasSpuKey(spuId?: string | null): spuId is string {
  return typeof spuId === 'string' && spuId.length > 0;
}

/**
 * 构造 SPU 详情链接 —— **唯一的深链拼装点**。
 *
 * @returns `/spu/{spuId}`；**键缺失时返回 `null`**（调用方不得渲染链接）
 */
export function spuHref(spuId?: string | null): string | null {
  return hasSpuKey(spuId) ? `/${SPU_DETAIL_SEGMENT}/${spuId}` : null;
}
