/**
 * 商城 — 分类筛选 + 服务端渲染 (ISR)
 * Server Component: SEO 友好, 每个分类独立缓存 60s
 */

import { getCategories, getProducts } from '@icedmall/api';
import { ProductCard, SectionReveal } from '@icedmall/ui';
import { MarketplaceInfinite } from './infinite-client';

export const revalidate = 60;

interface Props {
  searchParams: Promise<{ categoryId?: string; sort?: string; page?: string }>;
}

export default async function MarketplacePage({ searchParams }: Props) {
  const params = await searchParams;
  const categoryId = params.categoryId ? Number(params.categoryId) : undefined;
  const sort = params.sort || 'sales';
  const initialPage = Number(params.page || 1);

  let categories, products;
  try {
    [categories, products] = await Promise.all([
      getCategories(),
      getProducts({ categoryId, sort, order: 'desc', size: 12, page: initialPage }),
    ]);
  } catch {
    categories = null;
    products = null;
  }

  const records = products?.records ?? [];
  const total = products?.total ?? 0;
  const totalPages = products?.pages ?? 0;
  const activeCategory = categories?.find((c) => c.id === categoryId);

  return (
    <div className="mx-auto max-w-7xl px-4 pb-20">
      {/* 分类 Tab 栏 */}
      {categories && (
        <SectionReveal>
          <div className="mt-8 flex gap-2 overflow-x-auto pb-2 scrollbar-hide">
            <a href="/marketplace"
              className={`shrink-0 px-4 py-2 text-sm rounded-full border transition-all ${
                !categoryId ? 'border-accent-green bg-accent-green text-white' : 'border-warm-200 text-warm-600 hover:border-warm-400 bg-white'
              }`}>全部</a>
            {categories.map((cat) => (
              <a key={cat.id} href={`/marketplace?categoryId=${cat.id}`}
                className={`shrink-0 px-4 py-2 text-sm rounded-full border transition-all ${
                  categoryId === cat.id ? 'border-accent-green bg-accent-green text-white' : 'border-warm-200 text-warm-600 hover:border-warm-400 bg-white/80 backdrop-blur-sm hover:shadow-sm'
                }`}>{cat.name}</a>
            ))}
          </div>
        </SectionReveal>
      )}

      {/* 标题 */}
      <SectionReveal delay={50}>
        <div className="mt-6 mb-4">
          <h2 className="text-xl font-medium text-ink-black">{activeCategory ? activeCategory.name : '全部商品'}</h2>
          <p className="text-xs text-warm-400 mt-1">共 {total} 件</p>
        </div>
      </SectionReveal>

      {/* 商品网格 + 无限滚动客户端层 */}
      {records.length > 0 ? (
        <SectionReveal delay={100}>
          <MarketplaceInfinite
            categoryId={categoryId}
            sort={sort}
            initialProducts={records}
            initialPage={initialPage}
            totalPages={totalPages}
          />
        </SectionReveal>
      ) : (
        <div className="text-center py-20">
          <div className="text-6xl select-none">🍃</div>
          <p className="mt-4 text-warm-600">该分类暂无商品</p>
          <a href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">查看全部商品 →</a>
        </div>
      )}
    </div>
  );
}
