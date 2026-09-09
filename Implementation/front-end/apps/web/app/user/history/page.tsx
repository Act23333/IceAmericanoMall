'use client';

import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { apiClient } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import Link from 'next/link';

export default function HistoryPage() {
  const qc = useQueryClient();
  const { data: ids } = useQuery({
    queryKey: ['history'],
    queryFn: () => apiClient<number[]>('/api/user/history?size=20'),
  });

  const clearMutation = useMutation({
    mutationFn: () => apiClient('/api/user/history', { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['history'] }),
  });

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-light text-ink-black">浏览历史</h1>
        {(ids?.length ?? 0) > 0 && <Button size="sm" variant="secondary" onClick={() => clearMutation.mutate()}>清空历史</Button>}
      </div>

      {(!ids || ids.length === 0) ? (
        <div className="text-center py-20"><div className="text-6xl">🕐</div><p className="mt-4 text-warm-600">暂无浏览记录</p><Link href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">去逛逛</Link></div>
      ) : (
        <div className="space-y-2">
          {ids.map((id, i) => (
            <Link key={i} href={`/product/${id}`}>
              <GlassCard className="flex items-center gap-4 p-4 hover:shadow-glass-lg transition-shadow" blur="sm">
                <span className="text-warm-400 text-sm">{i + 1}</span>
                <span className="flex-1 text-sm text-ink-black">商品 #{id}</span>
                <span className="text-warm-400 text-sm">›</span>
              </GlassCard>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
