'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import { Coins, TrendingUp } from 'lucide-react';

/**
 * 积分中心 — V5.0
 *
 * 展示: 积分余额 + 积分明细(获取/消费记录)
 */
export default function PointsPage() {
  const [page, setPage] = useState(1);

  const { data: balance } = useQuery<{ userId: number; balance: number }>({
    queryKey: ['points', 'balance'],
    queryFn: () => apiClient('/api/user/points/balance'),
    staleTime: 60_000,
  });

  const { data: history, isLoading } = useQuery<any>({
    queryKey: ['points', 'history', page],
    queryFn: () => apiClient(`/api/user/points/history?page=${page}&size=20`),
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
        <p className="text-4xl font-light text-ink-black price">{balance?.balance ?? 0}</p>
        <p className="mt-2 text-xs text-text-tertiary">签到、购物均可获得积分</p>
      </GlassCard>

      {/* 积分明细 */}
      <h2 className="text-sm font-medium text-text-secondary mb-3">积分明细</h2>
      {isLoading ? (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-14 rounded-xl bg-warm-gray-100 animate-pulse" />
          ))}
        </div>
      ) : history?.records?.length > 0 ? (
        <div className="space-y-2">
          {history.records.map((log: any, i: number) => (
            <GlassCard key={log.id || i} className="flex items-center justify-between p-3" blur="sm">
              <div className="flex items-center gap-3">
                <div className={`flex h-8 w-8 items-center justify-center rounded-lg ${log.type === 1 ? 'bg-accent/10 text-accent' : 'bg-warm-gray-200 text-text-tertiary'}`}>
                  <TrendingUp className="h-4 w-4" />
                </div>
                <div>
                  <p className="text-sm text-ink-black">{log.source || (log.type === 1 ? '获取积分' : '消费积分')}</p>
                  <p className="text-xs text-text-tertiary">{log.createTime ? new Date(log.createTime).toLocaleDateString() : ''}</p>
                </div>
              </div>
              <span className={`text-sm font-medium ${log.type === 1 ? 'text-accent' : 'text-text-tertiary'}`}>
                {log.type === 1 ? '+' : '-'}{log.points}
              </span>
            </GlassCard>
          ))}
        </div>
      ) : (
        <div className="text-center py-12">
          <p className="text-text-tertiary text-sm">暂无积分记录</p>
        </div>
      )}

      {/* 分页 */}
      {history && history.pages > 1 && (
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
