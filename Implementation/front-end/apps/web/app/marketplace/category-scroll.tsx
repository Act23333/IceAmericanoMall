'use client';

import { useRef } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import type { CategoryVO } from '@icedmall/api';
import { ChevronLeft, ChevronRight } from 'lucide-react';

const CATEGORY_COLORS = [
  'from-amber-50 to-orange-100',
  'from-emerald-50 to-teal-100',
  'from-blue-50 to-indigo-100',
  'from-rose-50 to-pink-100',
  'from-violet-50 to-purple-100',
  'from-cyan-50 to-sky-100',
  'from-yellow-50 to-amber-100',
  'from-lime-50 to-green-100',
];

const CATEGORY_ICONS = ['📱', '💻', '👗', '🍜', '🏠', '🎮', '📚', '💄'];

export function CategoryScroll({ categories }: { categories: CategoryVO[] }) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const router = useRouter();

  const scroll = (dir: 'left' | 'right') => {
    if (!scrollRef.current) return;
    scrollRef.current.scrollBy({ left: dir === 'left' ? -280 : 280, behavior: 'smooth' });
  };

  if (!categories || categories.length === 0) return null;

  return (
    <section className="py-12">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-light text-ink-black">✨ 精选分类</h2>
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
        {categories.map((cat, i) => (
          <Link key={cat.id} href={`/marketplace?categoryId=${cat.id}`} scroll={false}
            className="shrink-0 w-44 rounded-2xl overflow-hidden group cursor-pointer"
            style={{ scrollSnapAlign: 'start' }}>
            <div className={`h-24 flex items-center justify-center bg-gradient-to-br ${CATEGORY_COLORS[i % CATEGORY_COLORS.length]} transition-transform duration-300 group-hover:scale-105`}>
              <span className="text-4xl select-none">{CATEGORY_ICONS[i % CATEGORY_ICONS.length]}</span>
            </div>
            <div className="mt-2 px-1">
              <p className="text-sm font-medium text-ink-black truncate">{cat.name}</p>
              <p className="text-xs text-warm-400 mt-0.5">查看全部 →</p>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
