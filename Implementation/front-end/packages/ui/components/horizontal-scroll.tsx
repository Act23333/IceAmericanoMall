'use client';

import { useRef, useState, useEffect, useCallback } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '../lib/utils';

interface HorizontalScrollProps {
  title: string;
  subtitle?: string;
  href?: string;
  children: React.ReactNode;
  className?: string;
}

/**
 * 横向滚动商品区 — 带左右箭头
 *
 * 品牌风格：精选推荐横滑，每张卡片 peek 下一页
 * GPU-friendly: scrollTo({ behavior: 'smooth' })
 */
export function HorizontalScroll({
  title,
  subtitle,
  href,
  children,
  className,
}: HorizontalScrollProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(true);

  const updateButtons = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 4);
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 4);
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.addEventListener('scroll', updateButtons, { passive: true });
    updateButtons();
    return () => el.removeEventListener('scroll', updateButtons);
  }, [updateButtons, children]);

  const scroll = (direction: 'left' | 'right') => {
    const el = scrollRef.current;
    if (!el) return;
    const cardWidth = 260 + 16; // card width + gap
    const scrollAmount = direction === 'left' ? -cardWidth * 2 : cardWidth * 2;
    el.scrollBy({ left: scrollAmount, behavior: 'smooth' });
  };

  return (
    <section className={cn('py-8', className)}>
      {/* 标题栏 */}
      <div className="flex items-end justify-between mb-6">
        <div>
          <h3 className="text-xl font-medium text-ink-black">{title}</h3>
          {subtitle && <p className="mt-1 text-sm text-warm-600">{subtitle}</p>}
        </div>
        <div className="flex items-center gap-2">
          {href && (
            <a
              href={href}
              className="text-sm text-accent-green hover:text-accent-green-light font-medium transition-colors hidden md:inline"
            >
              查看全部
            </a>
          )}
          {/* 箭头按钮 — 仅桌面端显示 */}
          <div className="hidden md:flex gap-1">
            <button
              onClick={() => scroll('left')}
              disabled={!canScrollLeft}
              className="rounded-full border border-warm-200 bg-white p-1.5 text-ink-soft transition-all hover:border-warm-400 disabled:opacity-30 disabled:cursor-not-allowed"
              aria-label="向左滚动"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              onClick={() => scroll('right')}
              disabled={!canScrollRight}
              className="rounded-full border border-warm-200 bg-white p-1.5 text-ink-soft transition-all hover:border-warm-400 disabled:opacity-30 disabled:cursor-not-allowed"
              aria-label="向右滚动"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>

      {/* 横滑容器 */}
      <div
        ref={scrollRef}
        className="flex gap-4 overflow-x-auto scroll-smooth snap-x snap-mandatory scrollbar-hide pb-2"
        style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
      >
        {children}
      </div>
    </section>
  );
}
