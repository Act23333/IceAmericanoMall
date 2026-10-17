/**
 * 商品详情加载 — 骨架屏
 */
export default function ProductDetailLoading() {
  return (
    <div className="mx-auto max-w-7xl px-4 pt-24 pb-20">
      <div className="grid gap-10 md:grid-cols-2">
        <div className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
        <div className="space-y-4">
          <div className="h-4 w-20 rounded bg-warm-100 animate-glass-shimmer" />
          <div className="h-8 w-3/4 rounded bg-warm-100 animate-glass-shimmer" />
          <div className="h-4 w-full rounded bg-warm-100 animate-glass-shimmer" />
          <div className="h-4 w-2/3 rounded bg-warm-100 animate-glass-shimmer" />
          <div className="h-8 w-32 rounded bg-warm-100 animate-glass-shimmer mt-6" />
          <div className="flex gap-2 mt-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-9 w-20 rounded-xl bg-warm-100 animate-glass-shimmer" />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
