'use client';

import { useState, useEffect } from 'react';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { apiClient } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import Link from 'next/link';

export default function HistoryPage() {
  const qc = useQueryClient();
  const { data: ids, isLoading } = useQuery({
    queryKey: ['history'],
    queryFn: () => apiClient<number[]>('/api/user/history?size=20'),
    staleTime: 30_000,
  });

  const [products, setProducts] = useState<Record<number, any>>({});

  // 拿到ID列表后，逐个查询商品名称
  useEffect(() => {
    if (!ids || ids.length === 0) return;
    const base = process.env.NEXT_PUBLIC_API_URL || '';
    ids.forEach(async (id) => {
      if (products[id]) return;
      try {
        const res = await fetch(`${base}/api/item/product/${id}`, { credentials: 'include' });
        const data = await res.json();
        if (data.code === 200 && data.data) {
          setProducts((p) => ({ ...p, [id]: data.data }));
        }
      } catch {}
    });
  }, [ids]);

  const clearMutation = useMutation({
    mutationFn: () => apiClient('/api/user/history', { method: 'DELETE' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['history'] }); setProducts({}); },
  });

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-light text-ink-black">浏览历史</h1>
        {(ids?.length ?? 0) > 0 && <Button size="sm" variant="secondary" onClick={() => clearMutation.mutate()}>清空历史</Button>}
      </div>

      {isLoading ? (
        <div className="space-y-3">{[1,2,3].map(i => <div key={i} className="h-16 rounded-xl bg-warm-gray-100 animate-pulse" />)}</div>
      ) : (!ids || ids.length === 0) ? (
        <div className="text-center py-20">
          <div className="text-6xl select-none">🕐</div>
          <p className="mt-4 text-text-secondary">暂无浏览记录</p>
          <Link href="/marketplace" className="mt-3 inline-block text-sm text-accent hover:underline">去逛逛 →</Link>
        </div>
      ) : (
        <div className="space-y-2">
          {ids.map((id, i) => {
            const p = products[id];
            return (
              <Link key={i} href={`/product/${id}`}>
                <GlassCard className="flex items-center gap-4 p-3 hover:shadow-glass-lg transition-shadow" blur="sm">
                  <span className="text-text-tertiary text-xs w-6 text-center">{i + 1}</span>
                  {p?.mainImage && (
                    <img src={p.mainImage} alt="" className="h-10 w-10 rounded-lg object-cover bg-warm-gray-100" />
                  )}
                  <span className="flex-1 text-sm text-ink-black truncate">
                    {p ? p.name : `商品 #${id}`}
                  </span>
                  {p && <span className="text-sm font-medium text-ink-black">¥{formatPrice(p.skus?.[0]?.price ?? 0)}</span>}
                  <span className="text-text-tertiary text-sm">›</span>
                </GlassCard>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
