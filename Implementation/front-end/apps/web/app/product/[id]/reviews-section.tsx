'use client';

import { useProductReviews } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import { timeAgo } from '@icedmall/utils';

export function ReviewsSection({ productId }: { productId: number }) {
  const { data, isLoading } = useProductReviews(productId);

  return (
    <div className="mt-24 border-t border-warm-200 pt-16">
      <h2 className="text-xl font-medium text-ink-black mb-6">商品评价</h2>
      {isLoading ? (
        <div className="space-y-3">{[1,2].map(i => <div key={i} className="h-20 rounded-xl bg-warm-100 animate-glass-shimmer" />)}</div>
      ) : (data?.records?.length ?? 0) === 0 ? (
        <p className="text-sm text-warm-400 text-center py-8">暂无评价</p>
      ) : (
        <div className="space-y-4">
          {data?.records?.map((r) => (
            <GlassCard key={r.id} className="p-4" blur="sm">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-sm font-medium text-ink-black">用户 #{r.userId}</span>
                <span className="text-yellow-500 text-xs">{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</span>
                <span className="text-xs text-warm-400 ml-auto">{r.createTime ? timeAgo(r.createTime) : ''}</span>
              </div>
              <p className="text-sm text-ink-soft leading-relaxed">{r.content}</p>
            </GlassCard>
          ))}
        </div>
      )}
    </div>
  );
}
