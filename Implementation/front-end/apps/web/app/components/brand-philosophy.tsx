/**
 * Section 1 — 品牌理念
 *
 * 视觉：大量留白 + 大号文字 + SVG 叶片装饰
 * 技术：Server Component (纯静态)
 */
export function BrandPhilosophy() {
  return (
    <section className="relative overflow-hidden bg-mist-white py-32 md:py-48">
      {/* 装饰 — 简约叶片 SVG (内联，无网络请求) */}
      <div className="absolute -top-20 right-0 text-accent-green/10 select-none pointer-events-none" aria-hidden="true">
        <svg width="400" height="400" viewBox="0 0 400 400" fill="none">
          <path
            d="M200 50 C260 80 350 150 300 250 C250 350 150 320 100 250 C50 180 100 80 200 50Z"
            fill="currentColor"
            opacity="0.3"
          />
          <path
            d="M220 100 C270 130 320 190 280 270 C240 340 160 300 120 240 C80 180 140 110 220 100Z"
            fill="currentColor"
            opacity="0.2"
          />
        </svg>
      </div>

      <div className="mx-auto max-w-3xl px-6 text-center">
        <h2 className="text-3xl md:text-5xl font-light text-ink-black leading-tight tracking-tight">
          我们相信
        </h2>
        <p className="mt-8 text-xl md:text-3xl font-light text-ink-soft leading-relaxed">
          科技不应喧宾夺主。
        </p>
        <p className="mt-6 text-base md:text-lg text-warm-600 leading-relaxed max-w-xl mx-auto">
          好的设计，像清晨的露水、午后的光线、夜晚的静谧 —— 它自然地存在于你的生活中，
          不刻意，不张扬，却在每一个细节里让你感受到品质。
        </p>
        <p className="mt-6 text-sm text-warm-400 tracking-wide">
          冰美商城的每一件商品，都是对这个理念的实践。
        </p>
      </div>

      {/* 底部装饰线 — 金色微光 */}
      <div className="absolute bottom-0 left-1/2 -translate-x-1/2 h-px w-24 bg-gradient-to-r from-transparent via-accent-gold/30 to-transparent" />
    </section>
  );
}
