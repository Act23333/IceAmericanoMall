/**
 * 商城首页 — 商品发现
 *
 * ISR: 60s 重新生成
 * 数据: 服务端 fetch → getCategories + getProducts
 *
 * 结构: 分类入口 → 新品横滑 → 精选横滑
 */

import { getCategories, getProducts } from '@icedmall/api';
import { ProductCard, HorizontalScroll, CategoryCard, SectionReveal } from '@icedmall/ui';

export const revalidate = 60;

export default async function MarketplacePage() {
  // ── 并行获取数据 (API 不可用时优雅降级) ──
  let categories, newProducts, hotProducts;
  try {
    [categories, newProducts, hotProducts] = await Promise.all([
      getCategories(),
      getProducts({ sort: 'sales', order: 'desc', size: 12 }),
      getProducts({ sort: 'price', order: 'asc', size: 12 }),
    ]);
  } catch {
    // API 不可用 — 显示空状态
    categories = null;
    newProducts = null;
    hotProducts = null;
  }

  const hasCategories = categories && categories.length > 0;
  const hasNewProducts = newProducts?.records && newProducts.records.length > 0;
  const hasHotProducts = hotProducts?.records && hotProducts.records.length > 0;

  return (
    <div className="mx-auto max-w-7xl px-4 pb-20">
      {/* ── Hero Banner ── */}
      <SectionReveal>
        <div className="mt-8 mb-12 rounded-3xl bg-gradient-to-br from-warm-50 via-mist-50 to-accent-green/5 p-12 md:p-20 text-center">
          <h1 className="text-3xl md:text-5xl font-light text-ink-black tracking-tight">
            发现生活之美
          </h1>
          <p className="mt-3 text-base text-warm-600 max-w-md mx-auto">
            每一件好物，都是一段故事的开始
          </p>
        </div>
      </SectionReveal>

      {/* ── 分类入口网格 ── */}
      {hasCategories && (
        <SectionReveal delay={100}>
          <div className="grid grid-cols-3 md:grid-cols-6 gap-4">
            {categories!.map((cat) => (
              <CategoryCard key={cat.id} category={cat} />
            ))}
          </div>
        </SectionReveal>
      )}

      {/* ── 热销推荐 横滑 ── */}
      {hasNewProducts && (
        <SectionReveal delay={200}>
          <HorizontalScroll
            title="热销推荐"
            subtitle="大家都在买的好物"
            href="/marketplace?sort=sales&order=desc"
          >
            {newProducts!.records.map((product) => (
              <div key={product.id} className="snap-start shrink-0 w-[260px]">
                <ProductCard
                  product={product}
                  variant="glass"
                  onClick={() => {
                    if (typeof window !== 'undefined') {
                      window.location.href = `/product/${product.id}`;
                    }
                  }}
                />
              </div>
            ))}
          </HorizontalScroll>
        </SectionReveal>
      )}

      {/* ── 精选好价 横滑 ── */}
      {hasHotProducts && (
        <SectionReveal delay={300}>
          <HorizontalScroll
            title="精选好价"
            subtitle="品质与价格兼得"
            href="/marketplace?sort=price&order=asc"
          >
            {hotProducts!.records.map((product) => (
              <div key={product.id} className="snap-start shrink-0 w-[260px]">
                <ProductCard
                  product={product}
                  variant="glass"
                  onClick={() => {
                    if (typeof window !== 'undefined') {
                      window.location.href = `/product/${product.id}`;
                    }
                  }}
                />
              </div>
            ))}
          </HorizontalScroll>
        </SectionReveal>
      )}

      {/* ── 空状态 ── */}
      {!hasCategories && !hasNewProducts && !hasHotProducts && (
        <div className="flex flex-col items-center justify-center py-32 text-center">
          <div className="text-6xl select-none">🌿</div>
          <h2 className="mt-4 text-xl font-light text-ink-black">暂无商品</h2>
          <p className="mt-2 text-sm text-warm-600">商品正在上架中，请稍后再来</p>
        </div>
      )}
    </div>
  );
}
