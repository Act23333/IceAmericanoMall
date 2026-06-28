'use client';

import { useEffect, useRef } from 'react';

interface ParallaxLightProps {
  /** 容器引用，光效只在此容器内跟随 */
  containerRef: React.RefObject<HTMLElement | null>;
  /** 光晕颜色 */
  color?: string;
  /** 光晕大小 */
  size?: number;
  /** 透明度 */
  opacity?: number;
}

/**
 * 鼠标跟随光效 — GPU-friendly 视差光晕
 *
 * 仅在 hover 容器时显示
 * will-change: transform 隔离合成层
 */
export function ParallaxLight({
  containerRef,
  color = 'rgba(180, 160, 200, 0.15)',
  size = 500,
  opacity = 0.5,
}: ParallaxLightProps) {
  const lightRef = useRef<HTMLDivElement>(null);
  const tickRef = useRef(false);

  useEffect(() => {
    const container = containerRef.current;
    const light = lightRef.current;
    if (!container || !light) return;

    const handleMove = (e: MouseEvent) => {
      if (tickRef.current) return;
      tickRef.current = true;
      requestAnimationFrame(() => {
        const rect = container.getBoundingClientRect();
        const x = e.clientX - rect.left - size / 2;
        const y = e.clientY - rect.top - size / 2;
        light.style.transform = `translate(${x}px, ${y}px)`;
        tickRef.current = false;
      });
    };

    const handleLeave = () => {
      light.style.opacity = '0';
    };
    const handleEnter = () => {
      light.style.opacity = String(opacity);
    };

    container.addEventListener('mousemove', handleMove, { passive: true });
    container.addEventListener('mouseleave', handleLeave);
    container.addEventListener('mouseenter', handleEnter);

    return () => {
      container.removeEventListener('mousemove', handleMove);
      container.removeEventListener('mouseleave', handleLeave);
      container.removeEventListener('mouseenter', handleEnter);
    };
  }, [containerRef, size, opacity]);

  return (
    <div
      ref={lightRef}
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 -z-10 opacity-0 transition-opacity duration-500"
      style={{
        background: `radial-gradient(${size}px circle, ${color}, transparent 70%)`,
        willChange: 'transform',
        width: size * 2,
        height: size * 2,
        top: 0,
        left: 0,
      }}
    />
  );
}
