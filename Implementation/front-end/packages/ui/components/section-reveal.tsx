'use client';

import { useEffect, useRef, useState } from 'react';
import { cn } from '../lib/utils';

interface SectionRevealProps {
  children: React.ReactNode;
  className?: string;
  /** 'up' = 从下淡入 | 'fade' = 纯淡入 */
  direction?: 'up' | 'fade';
  /** 延迟 (ms) */
  delay?: number;
  /** 触发比例 (0-1) */
  threshold?: number;
}

/**
 * Scroll-triggered reveal wrapper
 *
 * GPU-friendly: 仅使用 opacity + translateY
 * 支持 prefers-reduced-motion 自动关闭动画
 */
export function SectionReveal({
  children,
  className,
  direction = 'up',
  delay = 0,
  threshold = 0.15,
}: SectionRevealProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    // 尊重用户动效偏好
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) {
      setVisible(true);
      return;
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          setTimeout(() => setVisible(true), delay);
          observer.unobserve(node);
        }
      },
      { threshold },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, [delay, threshold]);

  return (
    <div
      ref={ref}
      className={cn(
        'transition-all duration-700 ease-[cubic-bezier(0.4,0,0.2,1)]',
        !visible && (direction === 'up'
          ? 'opacity-0 translate-y-6'
          : 'opacity-0'),
        visible && 'opacity-100 translate-y-0',
        className,
      )}
    >
      {children}
    </div>
  );
}
