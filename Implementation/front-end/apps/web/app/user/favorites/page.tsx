'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import Link from 'next/link';

export default function FavoritesPage() {
  const [page] = useState(1);
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['favorites', page],
    queryFn: () => apiClient<PageResult<{id:number;productId:number;createTime:string}>>(`/api/user/favorite?page=${page}&size=12`),
  });

  const removeMutation = useMutation({
    mutationFn: (productId: number) => apiClient(`/api/user/favorite?productId=${productId}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['favorites'] }),
  });

  return (
    <div className="mx-auto max-w-6xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-6">我的收藏</h1>
      {isLoading ? (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[1,2,3,4].map(i => <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />)}
        </div>
      ) : (data?.records?.length ?? 0) === 0 ? (
        <div className="text-center py-20"><div className="text-6xl">⭐</div><p className="mt-4 text-warm-600">暂无收藏</p><Link href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">去逛逛</Link></div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {data?.records?.map((fav) => (
            <GlassCard key={fav.id} className="relative group p-4 text-center" blur="sm">
              <Link href={`/product/${fav.productId}`} className="block">
                <div className="text-4xl mb-3">📦</div>
                <p className="text-sm text-ink-soft">商品 #{fav.productId}</p>
                <p className="text-xs text-warm-400 mt-1">点击查看详情</p>
              </Link>
              <button onClick={(e) => { e.stopPropagation(); removeMutation.mutate(fav.productId); }}
                className="absolute top-2 right-2 p-1.5 rounded-full bg-white/80 text-danger opacity-0 group-hover:opacity-100 transition-opacity text-xs">
                ✕
              </button>
            </GlassCard>
          ))}
        </div>
      )}
    </div>
  );
}
