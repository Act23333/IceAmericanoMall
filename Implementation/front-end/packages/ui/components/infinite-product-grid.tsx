'use client';

import { useEffect, useRef, useCallback } from 'react';
import { cn } from '../lib/utils';

interface InfiniteProductGridProps {
  children: React.ReactNode;
  className?: string;
  hasMore: boolean;
  isLoading: boolean;
  onLoadMore: () => void;
}

/**
 * 无限滚动商品网格
 * IntersectionObserver 触底自动加载下一页
 */
export function InfiniteProductGrid({
  children,
  className,
  hasMore,
  isLoading,
  onLoadMore,
}: InfiniteProductGridProps) {
  const sentinelRef = useRef<HTMLDivElement>(null);

  const handleIntersect = useCallback((entries: IntersectionObserverEntry[]) => {
    if (entries[0]?.isIntersecting && hasMore && !isLoading) {
      onLoadMore();
    }
  }, [hasMore, isLoading, onLoadMore]);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(handleIntersect, { rootMargin: '200px' });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [handleIntersect]);

  return (
    <div>
      <div className={cn('grid grid-cols-2 md:grid-cols-4 gap-4', className)}>
        {children}
      </div>
      {/* 触底哨兵 */}
      <div ref={sentinelRef} className="h-4 mt-4" />
      {isLoading && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
          ))}
        </div>
      )}
      {!hasMore && !isLoading && (
        <p className="text-center text-xs text-warm-400 mt-6">— 已经到底了 —</p>
      )}
    </div>
  );
}
