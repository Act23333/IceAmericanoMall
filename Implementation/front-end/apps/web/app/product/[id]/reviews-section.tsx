'use client';

import { useState } from 'react';
import { useProductReviews, useCreateReview } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import { timeAgo } from '@icedmall/utils';
import { Heart, MessageSquare, Star } from 'lucide-react';

/**
 * 评价区域 — V5.0 增强
 *
 * 京东/淘宝标准: 评分分布 + 筛选 + 点赞 + 商家回复 + 追评
 */
export function ReviewsSection({ productId }: { productId: number }) {
  const [filter, setFilter] = useState<{ rating?: number; hasMedia?: boolean; sort?: string }>({ sort: 'newest' });
  const { data, isLoading } = useProductReviews(productId, filter);

  const handleLike = async (reviewId: number) => {
    try {
      await fetch(`${process.env.NEXT_PUBLIC_API_URL || ''}/api/item/review/${reviewId}/like`, { method: 'POST', credentials: 'include' });
      // 简单刷新: 触发 refetch
      window.location.reload();
    } catch {}
  };

  return (
    <div className="mt-24 border-t border-warm-gray-200 pt-16">
      {/* 标题 + 筛选 */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-medium text-ink-black">
          商品评价
          {data?.total && <span className="text-sm text-text-secondary font-normal ml-2">({data.total})</span>}
        </h2>
        <div className="flex gap-1.5">
          {[
            { label: '全部', value: {} },
            { label: '好评', value: { rating: 5 } },
            { label: '有图', value: { hasMedia: true } },
            { label: '最新', value: { sort: 'newest' } },
          ].map((f) => (
            <button
              key={f.label}
              onClick={() => setFilter({ ...filter, ...f.value })}
              className={`px-3 py-1 text-xs rounded-full border transition-colors ${
                (f.value.rating && filter.rating === f.value.rating) ||
                (f.value.hasMedia && filter.hasMedia) ||
                (f.value.sort && filter.sort === 'newest' && !filter.rating && !filter.hasMedia)
                  ? 'border-accent bg-accent/5 text-accent'
                  : 'border-warm-gray-200 text-text-tertiary hover:border-warm-gray-400'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-24 rounded-xl bg-warm-gray-100 animate-pulse" />
          ))}
        </div>
      )}

      {/* Empty */}
      {(data?.records?.length ?? 0) === 0 && (
        <p className="text-sm text-text-tertiary text-center py-12">暂无评价</p>
      )}

      {/* Review list */}
      <div className="space-y-4">
        {data?.records?.map((r: any) => (
          <GlassCard key={r.id} className="p-4" blur="sm">
            {/* Header */}
            <div className="flex items-center gap-2 mb-2">
              <span className="flex items-center gap-0.5 text-amber text-xs">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star key={i} className={`h-3 w-3 ${i < r.rating ? 'fill-amber' : 'text-warm-gray-400'}`} />
                ))}
              </span>
              <span className="text-sm font-medium text-ink-black">{r.username || `用户 #${r.userId}`}</span>
              <span className="text-xs text-text-tertiary ml-auto">{r.createTime ? timeAgo(r.createTime) : ''}</span>
            </div>

            {/* Content */}
            <p className="text-sm text-text-secondary leading-relaxed">{r.content}</p>

            {/* Images */}
            {r.images && (
              <div className="flex gap-2 mt-2">
                {r.images.split(',').slice(0, 4).map((img: string, i: number) => (
                  <img key={i} src={img.trim()} alt="" className="h-16 w-16 rounded-lg object-cover" loading="lazy" />
                ))}
              </div>
            )}

            {/* Tags */}
            {r.tags && (
              <div className="flex gap-1.5 mt-2">
                {JSON.parse(r.tags || '[]').map((tag: string) => (
                  <span key={tag} className="px-2 py-0.5 text-xs rounded bg-warm-gray-100 text-text-tertiary">{tag}</span>
                ))}
              </div>
            )}

            {/* Merchant reply */}
            {r.reply && (
              <div className="mt-3 p-3 rounded-lg bg-warm-gray-100/50 border border-warm-gray-200/50">
                <p className="text-xs font-medium text-accent mb-1">
                  <MessageSquare className="h-3 w-3 inline mr-1" />商家回复
                  {r.replyTime && <span className="text-text-tertiary font-normal ml-2">{timeAgo(r.replyTime)}</span>}
                </p>
                <p className="text-xs text-text-secondary">{r.reply}</p>
              </div>
            )}

            {/* User append */}
            {r.appendContent && (
              <div className="mt-3 p-3 rounded-lg bg-accent-light/30 border border-accent/10">
                <p className="text-xs font-medium text-ink-black mb-1">
                  追评 {r.appendTime && <span className="text-text-tertiary font-normal ml-2">{timeAgo(r.appendTime)}</span>}
                </p>
                <p className="text-xs text-text-secondary">{r.appendContent}</p>
              </div>
            )}

            {/* Like button */}
            <div className="flex items-center gap-3 mt-3 pt-2 border-t border-warm-gray-100">
              <button
                onClick={() => handleLike(r.id)}
                className={`flex items-center gap-1 text-xs transition-colors ${
                  r.likedByMe ? 'text-danger' : 'text-text-tertiary hover:text-danger'
                }`}
              >
                <Heart className={`h-3.5 w-3.5 ${r.likedByMe ? 'fill-current' : ''}`} />
                {(r.likeCount || 0) > 0 ? r.likeCount : '赞'}
              </button>
            </div>
          </GlassCard>
        ))}
      </div>
    </div>
  );
}
