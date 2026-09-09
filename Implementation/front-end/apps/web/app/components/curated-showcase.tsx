'use client';

import { useRef } from 'react';
import { SectionReveal } from '@icedmall/ui';
import { ParallaxLight } from './parallax-light';

interface ShowcaseItem {
  title: string;
  tagline: string;
  image: string;
  href: string;
}

// 静态精选内容 — 品牌策展（非 API 驱动，保持首屏不依赖后端）
const CURATED_ITEMS: ShowcaseItem[] = [
  {
    title: '东方器物',
    tagline: '传统工艺与现代设计的交汇',
    image: '',
    href: '/marketplace?category=craft',
  },
  {
    title: '自然生活',
    tagline: '源于自然的材质与色彩',
    image: '',
    href: '/marketplace?category=natural',
  },
  {
    title: '科技美学',
    tagline: '让技术融入日常的每一个瞬间',
    image: '',
    href: '/marketplace?category=tech',
  },
];

/**
 * Section 2 — 精选展示
 *
 * 品牌风格：大卡片 + 一句话介绍 + 探索链接
 * 不是商品网格，是策展式的作品展示
 */
export function CuratedShowcase() {
  const sectionRef = useRef<HTMLElement>(null);

  return (
    <section
      ref={sectionRef}
      className="relative overflow-hidden bg-warm-50 py-32 md:py-40"
    >
      <ParallaxLight
        containerRef={sectionRef}
        color="rgba(196, 167, 71, 0.12)"
        size={400}
        opacity={0.4}
      />

      <div className="mx-auto max-w-6xl px-6">
        {/* 标题 */}
        <SectionReveal>
          <h2 className="text-center text-2xl md:text-4xl font-light text-ink-black tracking-tight">
            精选策展
          </h2>
          <p className="mt-3 text-center text-sm text-warm-600">
            每一件都是我们对品质的理解
          </p>
        </SectionReveal>

        {/* 卡片列表 — 3列布局 */}
        <div className="mt-16 grid gap-8 md:grid-cols-3">
          {CURATED_ITEMS.map((item, i) => (
            <SectionReveal key={item.title} delay={i * 100}>
              <a
                href={item.href}
                className="group block overflow-hidden rounded-2xl border border-warm-200 bg-white transition-all duration-300 hover:shadow-glass-lg hover:scale-[1.02]"
              >
                {/* 占位图 — 品牌色渐变 */}
                <div className="aspect-[4/5] w-full bg-gradient-to-br from-warm-100 via-mist-50 to-accent-green/10 flex items-center justify-center">
                  <div className="text-6xl select-none opacity-20">
                    {i === 0 ? '🏺' : i === 1 ? '🌿' : '✨'}
                  </div>
                </div>
                <div className="p-6">
                  <h3 className="text-lg font-medium text-ink-black">{item.title}</h3>
                  <p className="mt-1 text-sm text-warm-600">{item.tagline}</p>
                  <div className="mt-4 flex items-center gap-1 text-sm text-accent-green font-medium opacity-0 group-hover:opacity-100 transition-opacity duration-300">
                    探索
                    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                    </svg>
                  </div>
                </div>
              </a>
            </SectionReveal>
          ))}
        </div>
      </div>
    </section>
  );
}
