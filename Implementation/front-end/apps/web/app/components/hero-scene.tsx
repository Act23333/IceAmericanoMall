'use client';

import { useEffect, useRef, useCallback } from 'react';

/**
 * Hero 场景 — 品牌沉浸式首屏
 *
 * 视觉：全屏空间，三层 CSS 渐变模拟 白天→黄昏→夜晚 的时间流转
 * 交互：鼠标轻微视差（transform only）
 * 性能：无 canvas/视频/GIF，仅 opacity + transform
 *
 * 时间线（由 IntersectionObserver 驱动）：
 *   0%-33%  scroll  → 白天 (gradient-day)
 *   33%-66% scroll  → 黄昏 (gradient-dusk)
 *   66%-100% scroll → 夜晚 (gradient-night)
 */
export function HeroScene() {
  const containerRef = useRef<HTMLDivElement>(null);
  const dayRef = useRef<HTMLDivElement>(null);
  const duskRef = useRef<HTMLDivElement>(null);
  const nightRef = useRef<HTMLDivElement>(null);
  const lightRef = useRef<HTMLDivElement>(null);
  const tickRef = useRef(false);

  // ── 滚动驱动的时间变换 ──
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const day = dayRef.current;
    const dusk = duskRef.current;
    const night = nightRef.current;
    if (!day || !dusk || !night) return;

    // 尊重用户动效偏好
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) {
      day.style.opacity = '1';
      dusk.style.opacity = '0';
      night.style.opacity = '0';
      return;
    }

    const onScroll = () => {
      const rect = container.getBoundingClientRect();
      const heroHeight = rect.height;
      const scrolled = -rect.top; // 已滚过 Hero 的像素
      const progress = Math.max(0, Math.min(1, scrolled / heroHeight));

      // 三阶段淡入淡出
      if (progress < 0.25) {
        // 白天
        day.style.opacity = '1';
        dusk.style.opacity = String(Math.max(0, (progress - 0.15) / 0.1));
        night.style.opacity = '0';
      } else if (progress < 0.55) {
        // 黄昏
        day.style.opacity = String(Math.max(0, 1 - (progress - 0.25) / 0.1));
        dusk.style.opacity = '1';
        night.style.opacity = String(Math.max(0, (progress - 0.45) / 0.1));
      } else {
        // 夜晚
        day.style.opacity = '0';
        dusk.style.opacity = String(Math.max(0, 1 - (progress - 0.55) / 0.1));
        night.style.opacity = '1';
      }
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll(); // initial call
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // ── 鼠标光点视差 ──
  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (tickRef.current) return;
    tickRef.current = true;
    requestAnimationFrame(() => {
      const light = lightRef.current;
      if (!light) { tickRef.current = false; return; }

      const rect = containerRef.current?.getBoundingClientRect();
      if (!rect) { tickRef.current = false; return; }

      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const cx = rect.width / 2;
      const cy = rect.height / 2;

      // 微小的光点位移（±15px）
      const dx = ((x - cx) / cx) * 15;
      const dy = ((y - cy) / cy) * 15;
      light.style.transform = `translate(${dx}px, ${dy}px)`;

      tickRef.current = false;
    });
  }, []);

  return (
    <div
      ref={containerRef}
      onMouseMove={handleMouseMove}
      className="relative h-screen w-full overflow-hidden"
    >
      {/* ── 第1层: 白天 (暖白→暖灰渐变) ── */}
      <div
        ref={dayRef}
        className="absolute inset-0 transition-opacity duration-1000 ease-[cubic-bezier(0.4,0,0.2,1)]"
        style={{
          background: 'linear-gradient(180deg, #F7F6F3 0%, #E8E6E1 60%, #D4C5B9 100%)',
          opacity: 1,
        }}
      />

      {/* ── 第2层: 黄昏 (暖棕→蓝紫) ── */}
      <div
        ref={duskRef}
        className="absolute inset-0 transition-opacity duration-1000 ease-[cubic-bezier(0.4,0,0.2,1)]"
        style={{
          background: 'linear-gradient(180deg, #D4C5B9 0%, #A8A0C0 50%, #6B7DB3 100%)',
          opacity: 0,
        }}
      />

      {/* ── 第3层: 夜晚 (深蓝紫) ── */}
      <div
        ref={nightRef}
        className="absolute inset-0 transition-opacity duration-1000 ease-[cubic-bezier(0.4,0,0.2,1)]"
        style={{
          background: 'linear-gradient(180deg, #2D2D3F 0%, #1A1A2E 80%, #0D0D1A 100%)',
          opacity: 0,
        }}
      />

      {/* ── 鼠标跟随光晕 (仅装饰，GPU-friendly) ── */}
      <div
        ref={lightRef}
        className="pointer-events-none absolute -inset-24 opacity-30"
        style={{
          background: 'radial-gradient(600px circle at 50% 50%, rgba(212, 197, 185, 0.25), transparent 70%)',
          willChange: 'transform',
          transition: 'transform 0.3s ease-out',
        }}
      />

      {/* ── 中央内容 ── */}
      <div className="absolute inset-0 flex flex-col items-center justify-center px-4 text-center z-10">
        {/* 品牌名 */}
        <h1 className="text-5xl md:text-7xl font-light tracking-wide text-ink-black select-none"
          style={{ fontFamily: 'var(--font-geist-sans)' }}>
          冰美商城
        </h1>

        {/* 品牌理念 */}
        <p className="mt-6 max-w-md text-base md:text-lg text-ink-soft leading-relaxed"
          style={{ fontFamily: 'var(--font-geist-sans)' }}>
          Technology meets everyday life
        </p>
        <p className="mt-1 text-sm text-warm-600">
          科技自然融入生活
        </p>

        {/* CTA — 玻璃按钮 */}
        <a
          href="/marketplace"
          className="mt-10 inline-flex items-center gap-2 rounded-2xl border border-white/30 bg-white/20 px-8 py-3.5 text-sm font-medium text-ink-black backdrop-blur-xl transition-all duration-300 hover:bg-white/30 hover:scale-[1.03] active:scale-[0.98] select-none"
        >
          探索商城
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M17.25 8.25L21 12m0 0l-3.75 3.75M21 12H3" />
          </svg>
        </a>

        {/* 滚动指示器 */}
        <div className="absolute bottom-8 flex flex-col items-center gap-2 animate-fade-up" style={{ animationDelay: '1.5s' }}>
          <span className="text-xs text-warm-400">向下探索</span>
          <svg className="h-4 w-4 text-warm-400 animate-bounce" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
          </svg>
        </div>
      </div>
    </div>
  );
}
