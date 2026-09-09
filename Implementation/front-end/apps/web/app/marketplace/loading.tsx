/**
 * 商城加载骨架屏 — 品牌风格
 */
export default function MarketplaceLoading() {
  return (
    <div className="mx-auto max-w-7xl px-4 pb-20 pt-24">
      {/* Hero 骨架 */}
      <div className="h-48 rounded-3xl bg-warm-100 animate-glass-shimmer" />

      {/* 分类骨架 */}
      <div className="mt-12 grid grid-cols-6 gap-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-24 rounded-2xl bg-warm-100 animate-glass-shimmer" />
        ))}
      </div>

      {/* 横滑骨架 */}
      <div className="mt-12">
        <div className="h-6 w-32 rounded bg-warm-100 animate-glass-shimmer mb-4" />
        <div className="flex gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="w-[260px] shrink-0">
              <div className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
              <div className="mt-3 h-4 w-3/4 rounded bg-warm-100 animate-glass-shimmer" />
              <div className="mt-2 h-4 w-1/2 rounded bg-warm-100 animate-glass-shimmer" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
