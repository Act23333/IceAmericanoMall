/**
 * Section 4 — 进入商城入口
 *
 * 视觉：巨大的 CTA，从自然空间过渡到商城空间
 * 技术：Server Component
 */
export function MarketplacePortal() {
  return (
    <section className="relative overflow-hidden bg-gradient-to-b from-mist-white via-warm-50 to-warm-100 py-40 md:py-56">
      {/* 装饰 — 玻璃光晕 */}
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none select-none" aria-hidden="true">
        <div className="h-[600px] w-[600px] rounded-full bg-gradient-radial from-accent-blue-purple/8 via-transparent to-transparent blur-3xl" />
      </div>

      <div className="mx-auto max-w-2xl px-6 text-center relative z-10">
        <p className="text-sm text-warm-600 tracking-wide">
          准备好了吗？
        </p>

        <h2 className="mt-4 text-4xl md:text-6xl font-light text-ink-black tracking-tight">
          Enter Marketplace
        </h2>

        <p className="mt-6 text-base text-warm-600 leading-relaxed max-w-md mx-auto">
          从品牌世界，走进你的生活空间。
          探索精选好物，找到属于你的那一件。
        </p>

        {/* 进入商城 CTA */}
        <a
          href="/marketplace"
          className="mt-12 inline-flex items-center gap-3 rounded-2xl bg-accent-green px-10 py-4 text-white text-base font-medium shadow-lg shadow-accent-green/20 transition-all duration-300 hover:bg-accent-green-light hover:shadow-xl hover:shadow-accent-green/30 hover:scale-[1.03] active:scale-[0.98]"
        >
          进入商城
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
          </svg>
        </a>

        {/* 暗示文字 */}
        <p className="mt-8 text-xs text-warm-400">
          滚动继续探索品牌故事，或直接开始购物之旅
        </p>
      </div>
    </section>
  );
}
