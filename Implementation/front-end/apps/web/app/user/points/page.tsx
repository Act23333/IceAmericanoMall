'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient, type PageResult, type PointsBalanceVO, type PointsLogVO } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import { Coins, TrendingUp } from 'lucide-react';
import { formatPointsLog } from '@icedmall/utils';
import { LoadFailedState } from '@/app/components/load-failed-state';

/**
 * 积分中心 — `/api/points/balance` + `/api/points/history`（marketing-service）。
 *
 * ── 2026-10 B10：type 文案与金额符号（**这是一次静默失效的修复**）────────────
 * 旧实现自己猜映射：`log.type === 1 ? '获取积分' : '消费积分'`，金额符号也写死
 * `log.type === 1 ? '+' : '-'`。后端 `points_log.type` 的真源是
 * `Initialize.sql:581` / `PointsLog.java:24` / `PointsLogVO.java:23`
 * （`1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期`）⇒ `2`（下单）与 `3`（任务）**同样是获得**，
 * 却被显示成「消费积分」且金额带 `-`（+80 渲染成 `-80`，**符号反了**）。
 *
 * 现在的口径：
 *   - 类型文案走 `pointsLogTypeLabel()` / `formatPointsLog()`（唯一出口，逐值映射，
 *     表外取值显示**原始值** `类型 9`，不空白、不写「未知」吞掉）；
 *   - **正负号由 `points` 自身判定**（契约：正=获得、负=消耗），不再由 type 猜；
 *   - 失败态单独渲染（不再把请求失败显示成「暂无积分记录」）。
 *
 * ✅ 路径：积分域归 marketing-service，网关 `/api/points/**`（`/api/user/points/*` 不存在）。
 */

export default function PointsPage() {
  const [page, setPage] = useState(1);

  const {
    data: balance, isError: balanceError, isFetching: balanceFetching, refetch: refetchBalance,
  } = useQuery<PointsBalanceVO>({
    queryKey: ['points', 'balance'],
    queryFn: () => apiClient<PointsBalanceVO>('/api/points/balance'),
    staleTime: 60_000,
  });

  const {
    data: history, isLoading, isError, error, isFetching, refetch,
  } = useQuery<PageResult<PointsLogVO>>({
    queryKey: ['points', 'history', page],
    queryFn: () => apiClient<PageResult<PointsLogVO>>(`/api/points/history?page=${page}&size=20`),
  });

  return (
    <div className="mx-auto max-w-2xl px-4 pt-8 pb-20">
      <h1 className="text-xl font-light text-ink-black mb-6">积分中心</h1>

      {/* 积分余额卡片 */}
      <GlassCard className="p-6 mb-6 text-center" blur="md">
        <div className="flex items-center justify-center gap-2 mb-2">
          <Coins className="h-5 w-5 text-gold" />
          <span className="text-sm text-text-secondary">当前积分</span>
        </div>
        {balanceError ? (
          // 余额失败不伪装成 0（0 是「确实没有积分」的合法值）
          <div className="mt-2">
            <LoadFailedState
              compact
              title="积分余额加载失败"
              description="余额未能获取（不是 0 分）。"
              onRetry={() => { void refetchBalance(); }}
              retrying={balanceFetching}
            />
          </div>
        ) : (
          <p className="text-4xl font-light text-ink-black price">{balance?.balance ?? 0}</p>
        )}
        <p className="mt-2 text-xs text-text-tertiary">签到、购物均可获得积分</p>
      </GlassCard>

      {/* 积分明细 */}
      <h2 className="text-sm font-medium text-text-secondary mb-3">积分明细</h2>

      {/* 失败态（与空态可分 + 重试）*/}
      {isError && (
        <LoadFailedState
          title="积分明细加载失败"
          description="没能获取到积分流水（不是「没有记录」）。请重试。"
          detail={error instanceof Error ? error.message : undefined}
          onRetry={() => { void refetch(); }}
          retrying={isFetching}
        />
      )}

      {isLoading && !isError ? (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-14 rounded-xl bg-warm-gray-100 animate-pulse" />
          ))}
        </div>
      ) : !isError && (history?.records?.length ?? 0) > 0 ? (
        <div className="space-y-2">
          {history!.records.map((log, i) => {
            // 唯一出口：类型文案 + 符号 + 主文案（source 优先）全在这里定
            const face = formatPointsLog(log);
            return (
              <GlassCard key={log.id || i} className="flex items-center justify-between p-3" blur="sm">
                <div className="flex items-center gap-3">
                  <div className={`flex h-8 w-8 items-center justify-center rounded-lg ${face.isCredit ? 'bg-accent/10 text-accent' : 'bg-warm-gray-200 text-text-tertiary'}`}>
                    <TrendingUp className="h-4 w-4" />
                  </div>
                  <div>
                    <p className="text-sm text-ink-black">{face.primaryText}</p>
                    <p className="text-xs text-text-tertiary">
                      {/* 类型文案逐值映射；表外取值显示 `类型 9`（原始值可见） */}
                      <span className="mr-2">{face.typeText}</span>
                      {log.create_time ? new Date(log.create_time).toLocaleDateString() : ''}
                    </p>
                  </div>
                </div>
                <span className={`text-sm font-medium ${face.isCredit ? 'text-accent' : 'text-text-tertiary'}`}>
                  {face.signedPointsText}
                </span>
              </GlassCard>
            );
          })}
        </div>
      ) : !isError ? (
        <div className="text-center py-12">
          <p className="text-text-tertiary text-sm">暂无积分记录</p>
        </div>
      ) : null}

      {/* 分页 */}
      {!isError && history && history.pages > 1 && (
        <div className="flex justify-center gap-2 mt-6">
          <button
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-gray-200 disabled:opacity-30 hover:border-warm-gray-400 transition-colors"
          >
            上一页
          </button>
          <span className="px-3 py-1.5 text-sm text-text-secondary">{page}/{history.pages}</span>
          <button
            onClick={() => setPage((p) => Math.min(history.pages, p + 1))}
            disabled={page >= history.pages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-gray-200 disabled:opacity-30 hover:border-warm-gray-400 transition-colors"
          >
            下一页
          </button>
        </div>
      )}
    </div>
  );
}
