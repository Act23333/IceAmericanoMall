/**
 * 商城 — 分类筛选 + 服务端渲染 (ISR)
 * Server Component: SEO 友好, 每个分类独立缓存 60s
 */

import Link from 'next/link';
import { getCategories, getProducts } from '@icedmall/api';
import { SectionReveal } from '@icedmall/ui';
import { LoadFailedState } from '@/app/components/load-failed-state';
import { MarketplaceInfinite } from './infinite-client';
import { CategoryTabs } from './category-tabs';
import { ProductScroll } from './product-scroll';
import { FlashSaleBanner } from './flash-sale-banner';

export const revalidate = 60;

interface Props {
  searchParams: Promise<{ categoryId?: string; sort?: string; page?: string }>;
}

/**
 * 商城首页 —— 分类筛选 + 服务端渲染 (ISR)。
 *
 * ── 2026-10 B10：失败态（**这是一次静默失效的修复**）────────────────────────
 * 旧实现把三个请求塞进一个 `try/catch`，`catch` 里 `products = null` ⇒ 页面落到
 * 「该分类暂无商品」，把**加载失败**说成**没有商品**；段级 `marketplace/error.tsx`
 * 因此永远不会触发（错误在这里就被吃掉了）。
 *
 * 现在的口径：商品流失败 → 渲染 `LoadFailedState`（语义色 + 「重试」，缺省动作是
 * `router.refresh()`，即重新走 ISR 取数）。横幅 / 热卖条 / 分类栏是**旁路**信息，
 * 各自失败只降级隐身，不拖垮整页。
 */
export default async function MarketplacePage({ searchParams }: Props) {
  const params = await searchParams;
  /*
    数值转换定性（2026-10 B10 第 7 项）：
    这两处是**查询参数 → 数值**（不是路径参数，`[slug]` 才是路径参数），且目标字段在后端
    确实是数字：`item-service/adapter/request/SpuPageReq.java` 的 `categoryId` 是 `Long`、
    `page` 是 `Integer`（分类 id 即 `CategoryVO.id`，**不是** 32-hex 业务键）。
    ⇒ 转换合法；但 `Number('abc')` 会得 `NaN` 并原样发成 `category_id=NaN` ⇒ 后端绑定失败 400。
    故统一做 `Number.isFinite` 防护：非法入参视为「未传」，不把坏值送到后端。
  */
  const rawCategoryId = Number(params.categoryId);
  const categoryId = params.categoryId && Number.isFinite(rawCategoryId) ? rawCategoryId : undefined;
  const sort = params.sort || 'sales';
  const rawPage = Number(params.page);
  const initialPage = Number.isFinite(rawPage) && rawPage > 0 ? rawPage : 1;

  const [categoriesRes, productsRes, hotRes] = await Promise.allSettled([
    getCategories(),
    getProducts({ categoryId, sort, order: 'desc', size: 12, page: initialPage }),
    getProducts({ sort: 'sales', order: 'desc', size: 12 }),
  ]);

  const categories = categoriesRes.status === 'fulfilled' ? categoriesRes.value : null;
  // 热卖条 / 分类栏属**次级装饰区块**：失败即隐身，不宣称「暂无」（主商品流失败另走下方失败态）
  const hotProducts = hotRes.status === 'fulfilled' ? hotRes.value : null;
  const productsError = productsRes.status === 'rejected' ? productsRes.reason : null;
  const products = productsRes.status === 'fulfilled' ? productsRes.value : null;

  const records = products?.records ?? [];
  const total = products?.total ?? 0;
  const totalPages = products?.pages ?? 0;
  const activeCategory = categories?.find((c) => c.id === categoryId);

  return (
    <div className="mx-auto max-w-7xl px-4 pb-20">
      {/* 秒杀横幅 */}
      <FlashSaleBanner />

      {/* 热卖推荐 — 放在分类上面，切换分类不变 */}
      {hotProducts?.records && hotProducts.records.length > 0 && (
        <ProductScroll products={hotProducts.records} />
      )}

      {/* 分类 Tab 栏 — 客户端路由，不重载页面 */}
      {categories && (
        <SectionReveal>
          <div className="mt-8">
            <CategoryTabs categories={categories} />
          </div>
        </SectionReveal>
      )}

      {/* 标题 */}
      <SectionReveal delay={50}>
        <div className="mt-6 mb-4">
          <h2 className="text-xl font-medium text-ink-black">{activeCategory ? activeCategory.name : '全部商品'}</h2>
          <p className="text-xs text-warm-400 mt-1">
            {productsError ? '商品数量暂不可用' : `共 ${total} 件`}
          </p>
        </div>
      </SectionReveal>

      {/* 失败态 —— 先于空态判定（错误绝不能落到「该分类暂无商品」）*/}
      {productsError ? (
        <LoadFailedState
          title="商品加载失败"
          description="没能获取到商品列表（不是「该分类没有商品」）。请重试。"
          detail={productsError instanceof Error ? productsError.message : undefined}
        />
      ) : records.length > 0 ? (
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
          <Link href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">查看全部商品 →</Link>
        </div>
      )}
    </div>
  );
}
