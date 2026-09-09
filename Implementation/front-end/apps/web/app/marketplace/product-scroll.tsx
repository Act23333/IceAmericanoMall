'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import type { ProductVO } from '@icedmall/api';

function useCarousel(total: number, intervalMs = 3000) {
  const [idx, setIdx] = useState(0);
  const [paused, setPaused] = useState(false);

  const next = useCallback(() => setIdx((i) => (i + 1) % total), [total]);
  const prev = useCallback(() => setIdx((i) => (i - 1 + total) % total), [total]);

  useEffect(() => {
    if (paused || total <= 1) return;
    const t = setInterval(next, intervalMs);
    return () => clearInterval(t);
  }, [next, intervalMs, paused, total]);

  return { idx, setIdx, next, prev, paused, setPaused };
}

export function ProductScroll({ products }: { products: ProductVO[] }) {
  const total = products.length;
  const { idx, setIdx, setPaused } = useCarousel(total, 3500);

  if (total === 0) return null;

  return (
    <section className="py-12" suppressHydrationWarning>
      <div className="mb-6">
        <h2 className="text-xl font-light text-ink-black">🔥 大家都在买</h2>
        <p className="text-xs text-warm-400 mt-1">热卖推荐</p>
      </div>

      {/* 轮播视窗 */}
      <div className="relative overflow-hidden rounded-2xl bg-warm-50"
        onMouseEnter={() => setPaused(true)}
        onMouseLeave={() => setPaused(false)}>
        <div className="flex transition-transform duration-700 ease-in-out"
          style={{ transform: `translateX(-${idx * 100}%)` }}>
          {products.map((p) => (
            <Link key={p.id} href={`/product/${p.id}`} scroll={false}
              className="shrink-0 w-full grid grid-cols-1 md:grid-cols-2 gap-6 p-6 md:p-10 no-underline group">
              {/* 图片 */}
              <div className="aspect-square rounded-xl overflow-hidden bg-warm-100">
                <img src={p.mainImage} alt={p.name}
                  className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
                  loading="lazy" decoding="async" />
              </div>
              {/* 文案 */}
              <div className="flex flex-col justify-center">
                <p className="text-xs text-warm-400 uppercase tracking-widest">{p.brand || '冰美精选'}</p>
                <h3 className="mt-2 text-xl md:text-2xl font-light text-ink-black leading-snug">{p.name}</h3>
                <p className="mt-3 text-sm text-warm-600 line-clamp-2 leading-relaxed">{p.description}</p>
                <div className="mt-4 flex items-baseline gap-3">
                  <span className="text-lg text-accent-green font-medium">
                    ¥{(p.skus?.[0]?.price ?? 0) / 100}
                  </span>
                  <span className="text-xs text-warm-400">已售 {p.soldCount}</span>
                </div>
                <span className="mt-4 inline-block text-sm text-accent-green group-hover:underline">
                  查看详情 →
                </span>
              </div>
            </Link>
          ))}
        </div>
      </div>

      {/* 圆点指示器 — 大触摸区，可点击切换 */}
      <div className="mt-4 flex justify-center gap-3">
        {products.map((_, i) => (
          <button key={i} onClick={() => setIdx(i)} aria-label={`切换到第${i + 1}个`}
            className="p-1 rounded-full transition-all duration-300 focus:outline-none"
            style={{ padding: '6px' }}>
            <span className={`block rounded-full transition-all duration-300 ${
              i === idx
                ? 'w-6 h-2.5 bg-accent shadow-sm'
                : 'w-2.5 h-2.5 bg-warm-300 hover:bg-warm-400 hover:scale-125'
            }`} />
          </button>
        ))}
      </div>
    </section>
  );
}
