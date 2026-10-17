'use client';

import { Button, GlassCard } from '@icedmall/ui';

/**
 * SPU 详情段的**错误边界** —— 只接「暂时性失败」，不接「商品不存在」。
 *
 * 职责划分（2026-10-18 B8 明确，别混回去）：
 *   - **404 / 已删除** → `page.tsx` 的 `isNotFound()` 分支调 `notFound()`
 *     → 段级 `not-found.tsx`（「商品不存在或已删除」+ 真实 404 状态）；
 *   - **其它失败**（网关不可达 / 5xx / 超时 / JSON 解析失败）→ 落到这里
 *     （「暂时无法加载」+ `reset()` 重试）。
 * 后端 P2 已把「商品不存在」从「200 + 3011」改为**真 HTTP 404**；若这里不区分，
 * 临时故障会被误报成「商品不存在」，反过来则会让失效链接显示成「稍后重试」的死循环。
 */
export default function SpuDetailError({
  error: _error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="flex min-h-screen items-center justify-center px-4 pt-24">
      <GlassCard className="max-w-md p-10 text-center">
        <div className="text-4xl select-none">🌿</div>
        <h2 className="mt-4 text-lg font-medium text-ink-black">
          暂时无法加载
        </h2>
        <p className="mt-2 text-sm text-warm-600">
          商品详情加载失败，请稍后重试
        </p>
        <Button onClick={reset} variant="primary" size="sm" className="mt-6">
          重新加载
        </Button>
      </GlassCard>
    </div>
  );
}
