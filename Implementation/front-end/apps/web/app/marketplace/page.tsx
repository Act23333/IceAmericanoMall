/**
 * 商城 — 分类筛选 + 商品列表
 * ISR: 60s, 支持 ?categoryId= 查询参数过滤
 */

import { getCategories, getProducts } from '@icedmall/api';
import { ProductCard, CategoryCard, SectionReveal, InfiniteProductGrid } from '@icedmall/ui';

export const revalidate = 60;

interface Props {
  searchParams: Promise<{ categoryId?: string; sort?: string }>;
}

export default async function MarketplacePage({ searchParams }: Props) {
  const params = await searchParams;
  const categoryId = params.categoryId ? Number(params.categoryId) : undefined;
  const sort = (params.sort || 'sales') as string;

  let categories, products;
  try {
    [categories, products] = await Promise.all([
      getCategories(),
      getProducts({ categoryId, sort, order: 'desc', size: 24 }),
    ]);
  } catch {
    categories = null;
    products = null;
  }

  const activeCategory = categories?.find((c) => c.id === categoryId);
  const records = products?.records ?? [];

  return (
    <div className="mx-auto max-w-7xl px-4 pb-20">
      {/* 分类 Tab 栏 */}
      {categories && categories.length > 0 && (
        <SectionReveal>
          <div className="mt-8 flex gap-2 overflow-x-auto pb-2">
            <a
              href="/marketplace"
              className={`shrink-0 px-4 py-2 text-sm rounded-full border transition-all ${
                !categoryId
                  ? 'border-accent-green bg-accent-green text-white'
                  : 'border-warm-200 text-warm-600 hover:border-warm-400 bg-white'
              }`}
            >
              全部
            </a>
            {categories.map((cat) => (
              <a
                key={cat.id}
                href={`/marketplace?categoryId=${cat.id}`}
                className={`shrink-0 px-4 py-2 text-sm rounded-full border transition-all ${
                  categoryId === cat.id
                    ? 'border-accent-green bg-accent-green text-white'
                    : 'border-warm-200 text-warm-600 hover:border-warm-400 bg-white'
                }`}
              >
                {cat.name}
              </a>
            ))}
          </div>
        </SectionReveal>
      )}

      {/* 分类标题 */}
      <SectionReveal delay={50}>
        <div className="mt-6 mb-4">
          <h2 className="text-xl font-medium text-ink-black">
            {activeCategory ? activeCategory.name : '全部商品'}
          </h2>
          {activeCategory?.children && activeCategory.children.length > 0 && (
            <div className="flex gap-2 mt-2">
              {activeCategory.children.map((sub) => (
                <a
                  key={sub.id}
                  href={`/marketplace?categoryId=${sub.id}`}
                  className="text-xs text-warm-600 hover:text-accent-green transition-colors"
                >
                  {sub.name}
                </a>
              ))}
            </div>
          )}
        </div>
      </SectionReveal>

      {/* 商品网格 */}
      <SectionReveal delay={100}>
        {records.length > 0 ? (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {records.map((product) => (
              <ProductCard key={product.id} product={product} href={`/product/${product.id}`} />
            ))}
          </div>
        ) : (
          <div className="text-center py-20">
            <div className="text-6xl select-none">🍃</div>
            <p className="mt-4 text-warm-600">该分类暂无商品</p>
            <a href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">
              查看全部商品 →
            </a>
          </div>
        )}
      </SectionReveal>
    </div>
  );
}
