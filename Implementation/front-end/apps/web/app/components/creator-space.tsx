/**
 * Section 3 — 创造者空间
 *
 * 视觉：极简，玻璃卡片 + 留白
 * 技术：Server Component (纯静态)
 */
export function CreatorSpace() {
  return (
    <section className="relative overflow-hidden bg-mist-white py-32 md:py-40">
      {/* 背景微光 — 金色渐变圈 */}
      <div
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-none select-none"
        aria-hidden="true"
      >
        <div className="h-[500px] w-[500px] rounded-full bg-gradient-radial from-accent-gold/5 to-transparent" />
      </div>

      <div className="mx-auto max-w-2xl px-6 text-center relative z-10">
        {/* 玻璃卡片 */}
        <div className="rounded-3xl border border-white/20 bg-white/[0.06] backdrop-blur-2xl p-12 md:p-16 shadow-glass">
          <p className="text-xs tracking-[0.3em] text-warm-400 uppercase">
            Curated by
          </p>
          <h2 className="mt-4 text-2xl md:text-3xl font-light text-ink-black leading-relaxed">
            冰美团队
          </h2>
          <p className="mt-6 text-base text-warm-600 leading-relaxed">
            我们是一群相信「好产品自己会说话」的设计师、工程师与生活家。
          </p>
          <p className="mt-3 text-sm text-warm-400 leading-relaxed">
            我们不做广告轰炸、不做满屏优惠券。我们只做一件事：挑选、打磨、呈现那些
            真正值得你拥有的物品。
          </p>
          <div className="mt-8 flex items-center justify-center gap-4 text-xs text-warm-400">
            <span>东方美学</span>
            <span className="text-warm-200">•</span>
            <span>生活品质</span>
            <span className="text-warm-200">•</span>
            <span>可持续</span>
          </div>
        </div>
      </div>
    </section>
  );
}
