'use client';

import { useRef } from 'react';
import Link from 'next/link';
import type { ProductVO } from '@icedmall/api';
import { PriceDisplay } from '@icedmall/ui';
import { ChevronLeft, ChevronRight } from 'lucide-react';

export function ProductScroll({ products }: { products: ProductVO[] }) {
  const scrollRef = useRef<HTMLDivElement>(null);

  const scroll = (dir: 'left' | 'right') => {
    if (!scrollRef.current) return;
    scrollRef.current.scrollBy({ left: dir === 'left' ? -300 : 300, behavior: 'smooth' });
  };

  if (!products || products.length === 0) return null;

  return (
    <section className="py-12">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-xl font-light text-ink-black">🔥 热卖推荐</h2>
          <p className="text-xs text-warm-400 mt-1">大家都在买</p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => scroll('left')}
            className="p-2 rounded-full border border-warm-200 text-warm-500 hover:border-accent-green hover:text-accent-green transition-colors">
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button onClick={() => scroll('right')}
            className="p-2 rounded-full border border-warm-200 text-warm-500 hover:border-accent-green hover:text-accent-green transition-colors">
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div ref={scrollRef}
        className="flex gap-4 overflow-x-auto scrollbar-hide scroll-smooth pb-2"
        style={{ scrollSnapType: 'x mandatory' }}>
        {products.map((p) => (
          <Link key={p.id} href={`/product/${p.id}`} scroll={false}
            className="shrink-0 w-40 rounded-2xl overflow-hidden group cursor-pointer bg-white border border-warm-100 hover:border-accent-green/30 hover:shadow-lg transition-all duration-300"
            style={{ scrollSnapAlign: 'start' }}>
            <div className="aspect-square overflow-hidden bg-warm-50">
              <img src={p.mainImage} alt={p.name}
                className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110"
                loading="lazy" decoding="async" />
            </div>
            <div className="p-3">
              <p className="text-sm text-ink-black line-clamp-2 leading-snug">{p.name}</p>
              <div className="mt-2 flex items-baseline justify-between">
                <PriceDisplay cents={p.skus?.[0]?.price ?? 0} size="sm" />
                <span className="text-xs text-warm-400">已售 {p.soldCount}</span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
