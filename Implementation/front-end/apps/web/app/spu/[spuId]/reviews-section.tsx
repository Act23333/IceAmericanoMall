'use client';

import { useState } from 'react';
import { useProductReviews, REVIEW_SORT, type ReviewSort } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import { timeAgo } from '@icedmall/utils';
import { Heart, MessageSquare, Star, Image } from 'lucide-react';
import { LoadFailedState } from '@/app/components/load-failed-state';

const RATINGS = [
  { label: '全部', value: undefined },
  { label: '好评', value: 4 },
  { label: '中评', value: 3 },
  { label: '差评', value: 1 },
];

/**
 * 排序选项 —— **只列后端真的接受的取值**。
 *
 * 真源：item-service `domain/enums/ReviewSortEnum.java`
 * （`NEWEST("newest")` / `HIGHEST("highest")` / `LOWEST("lowest")`；
 * `ReviewFilterReq.sort` javadoc 同口径，缺省 `newest`）。
 *
 * ⚠️ 2026-10 B10 修复：旧实现在这里多给了一个 `{ label: '最早', value: 'oldest' }`，
 * 而后端**没有** `oldest` —— 更糟的是它不报错也不为空：`ReviewSortEnum.fromCode()`
 * 对未知码**静默回退 `NEWEST`** ⇒ 用户点「最早」看到的是「最新」，无任何提示。
 *
 * 「按时间正序（最旧优先）」属**后端能力缺口**（枚举里没有该取值），已在交付说明中登记；
 * 前端**不得**私自定义 `oldest` 之类的编码 —— 编了也只会被后端静默吞掉。
 * 取值本身由 `ReviewSort` 联合类型约束（非法码在**编译期**即被拒）。
 */
const SORTS: readonly { label: string; value: ReviewSort }[] = [
  { label: '最新', value: REVIEW_SORT.NEWEST },
  { label: '评分最高', value: REVIEW_SORT.HIGHEST },
  { label: '评分最低', value: REVIEW_SORT.LOWEST },
];

/**
 * `spuId` —— **SPU 业务键 32-hex**（`SpuVO.spu_id` / `ReviewVO.spu_id` 同口径）。
 *
 * 2026-10-18 B8：评价端点资源段 `review/product/{spuId}` → **`review/spu/{spuId}`**，
 * 且路径变量由技术键 `Long` 改为**业务键 `String`**
 * （`ReviewController` + `IdGenerator.requireWire`；形态非法即 400）。
 *
 * ⚠️ 因此调用方**必须原样传 String**：旧实现于页面内写 `Number(id)`，
 * 而 32-hex 过 `Number()` 得 `NaN` ⇒ 评价区整体失效（不报错、只显空态）。
 */
export function ReviewsSection({ spuId }: { spuId: string }) {
  const [rating, setRating] = useState<number | undefined>();
  const [hasMedia, setHasMedia] = useState(false);
  const [sort, setSort] = useState<ReviewSort>(REVIEW_SORT.NEWEST);

  const { data, isLoading, isError, error, isFetching, refetch } = useProductReviews(spuId, { rating, hasMedia: hasMedia || undefined, sort });

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
      {isLoading && !isError && (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-24 rounded-xl bg-warm-gray-100 animate-pulse" />
          ))}
        </div>
      )}

      {/* 失败态（区块级）—— 旧实现请求失败会落到「暂无评价」，与"确实没人评价"不可分辨 */}
      {isError && (
        <LoadFailedState
          compact
          title="评价加载失败"
          description="没能获取到评价列表（不是「暂无评价」）。请重试。"
          detail={error instanceof Error ? error.message : undefined}
          onRetry={() => { void refetch(); }}
          retrying={isFetching}
        />
      )}

      {/* Empty */}
      {!isError && (data?.records?.length ?? 0) === 0 && (
        <p className="text-sm text-text-tertiary text-center py-12">暂无评价</p>
      )}

      {/* Review list */}
      <div className="space-y-4">
        {!isError && data?.records?.map((r) => (
          <GlassCard key={r.id} className="p-4" blur="sm">
            <div className="flex items-center gap-2 mb-2">
              <span className="flex items-center gap-0.5 text-amber text-xs">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star key={i} className={`h-3 w-3 ${i < r.rating ? 'fill-amber' : 'text-warm-gray-400'}`} />
                ))}
              </span>
              <span className="text-sm font-medium text-ink-black">{r.username || `用户 #${r.user_id}`}</span>
              <span className="text-xs text-text-tertiary ml-auto">{r.create_time ? timeAgo(r.create_time) : ''}</span>
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
                  {r.reply_time && <span className="text-text-tertiary font-normal ml-2">{timeAgo(r.reply_time)}</span>}
                </p>
                <p className="text-xs text-text-secondary">{r.reply}</p>
              </div>
            )}

            {r.append_content && (
              <div className="mt-3 p-3 rounded-lg bg-accent-light/30 border border-accent/10">
                <p className="text-xs font-medium text-ink-black mb-1">
                  追评 {r.append_time && <span className="text-text-tertiary font-normal ml-2">{timeAgo(r.append_time)}</span>}
                </p>
                <p className="text-xs text-text-secondary">{r.append_content}</p>
              </div>
            )}

            <div className="flex items-center gap-3 mt-3 pt-2 border-t border-warm-gray-100">
              <button
                onClick={() => handleLike(r.id)}
                className={`flex items-center gap-1 text-xs transition-colors ${
                  r.liked_by_me ? 'text-danger' : 'text-text-tertiary hover:text-danger'
                }`}
              >
                <Heart className={`h-3.5 w-3.5 ${r.liked_by_me ? 'fill-current' : ''}`} />
                {(r.like_count || 0) > 0 ? r.like_count : '赞'}
              </button>
            </div>
          </GlassCard>
        ))}
      </div>
    </div>
  );
}