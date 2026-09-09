'use client';

import { useState } from 'react';
import { useProductReviews } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import { timeAgo } from '@icedmall/utils';
import { Heart, MessageSquare, Star, Image } from 'lucide-react';

const RATINGS = [
  { label: '全部', value: undefined },
  { label: '好评', value: 4 },
  { label: '中评', value: 3 },
  { label: '差评', value: 1 },
];
const SORTS = [
  { label: '最新', value: 'newest' },
  { label: '最早', value: 'oldest' },
];

export function ReviewsSection({ productId }: { productId: number }) {
  const [rating, setRating] = useState<number | undefined>();
  const [hasMedia, setHasMedia] = useState(false);
  const [sort, setSort] = useState('newest');

  const { data, isLoading } = useProductReviews(productId, { rating, hasMedia: hasMedia || undefined, sort });

  const handleLike = async (reviewId: number) => {
    try {
      const base = process.env.NEXT_PUBLIC_API_URL || '';
      await fetch(`${base}/api/item/review/${reviewId}/like`, { method: 'POST', credentials: 'include' });
      window.location.reload();
    } catch {}
  };

  return (
    <div className="mt-24 border-t border-warm-gray-200 pt-16">
      {/* 标题 */}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-medium text-ink-black">
          商品评价
          {data?.total != null && <span className="text-sm text-text-secondary font-normal ml-2">({data.total})</span>}
        </h2>
      </div>

      {/* 筛选栏 */}
      <div className="flex flex-wrap items-center gap-3 mb-6">
        {/* 评级 */}
        <span className="text-xs text-text-tertiary mr-1">评级</span>
        {RATINGS.map((r) => (
          <button
            key={r.label}
            onClick={() => setRating(r.value)}
            className={`px-3 py-1 text-xs rounded-full border transition-colors ${
              rating === r.value
                ? 'border-accent bg-accent/5 text-accent'
                : 'border-warm-gray-200 text-text-tertiary hover:border-warm-gray-400'
            }`}
          >
            {r.label}
          </button>
        ))}

        {/* 分隔 */}
        <span className="w-px h-4 bg-warm-gray-300 mx-1" />

        {/* 有图开关 */}
        <button
          onClick={() => setHasMedia(!hasMedia)}
          className={`flex items-center gap-1 px-3 py-1 text-xs rounded-full border transition-colors ${
            hasMedia
              ? 'border-accent bg-accent/5 text-accent'
              : 'border-warm-gray-200 text-text-tertiary hover:border-warm-gray-400'
          }`}
        >
          <Image className="h-3 w-3" />
          有图
        </button>

        {/* 分隔 */}
        <span className="w-px h-4 bg-warm-gray-300 mx-1" />

        {/* 排序 */}
        <span className="text-xs text-text-tertiary mr-1">排序</span>
        {SORTS.map((s) => (
          <button
            key={s.label}
            onClick={() => setSort(s.value)}
            className={`px-3 py-1 text-xs rounded-full border transition-colors ${
              sort === s.value
                ? 'border-accent bg-accent/5 text-accent'
                : 'border-warm-gray-200 text-text-tertiary hover:border-warm-gray-400'
            }`}
          >
            {s.label}
          </button>
        ))}
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
            <div className="flex items-center gap-2 mb-2">
              <span className="flex items-center gap-0.5 text-amber text-xs">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star key={i} className={`h-3 w-3 ${i < r.rating ? 'fill-amber' : 'text-warm-gray-400'}`} />
                ))}
              </span>
              <span className="text-sm font-medium text-ink-black">{r.username || `用户 #${r.userId}`}</span>
              <span className="text-xs text-text-tertiary ml-auto">{r.createTime ? timeAgo(r.createTime) : ''}</span>
            </div>

            <p className="text-sm text-text-secondary leading-relaxed">{r.content}</p>

            {r.images && (
              <div className="flex gap-2 mt-2">
                {r.images.split(',').slice(0, 4).map((img: string, i: number) => (
                  <img key={i} src={img.trim()} alt="" className="h-16 w-16 rounded-lg object-cover" loading="lazy" />
                ))}
              </div>
            )}

            {r.tags && (
              <div className="flex gap-1.5 mt-2">
                {(() => { try { return JSON.parse(r.tags); } catch { return []; } })().map((tag: string) => (
                  <span key={tag} className="px-2 py-0.5 text-xs rounded bg-warm-gray-100 text-text-tertiary">{tag}</span>
                ))}
              </div>
            )}

            {r.reply && (
              <div className="mt-3 p-3 rounded-lg bg-warm-gray-100/50 border border-warm-gray-200/50">
                <p className="text-xs font-medium text-accent mb-1">
                  <MessageSquare className="h-3 w-3 inline mr-1" />商家回复
                  {r.replyTime && <span className="text-text-tertiary font-normal ml-2">{timeAgo(r.replyTime)}</span>}
                </p>
                <p className="text-xs text-text-secondary">{r.reply}</p>
              </div>
            )}

            {r.appendContent && (
              <div className="mt-3 p-3 rounded-lg bg-accent-light/30 border border-accent/10">
                <p className="text-xs font-medium text-ink-black mb-1">
                  追评 {r.appendTime && <span className="text-text-tertiary font-normal ml-2">{timeAgo(r.appendTime)}</span>}
                </p>
                <p className="text-xs text-text-secondary">{r.appendContent}</p>
              </div>
            )}

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
