/**
 * SPU 详情页 —— `/spu/[spuId]`
 *
 * ISR: 120s 重新生成
 * generateStaticParams: 预渲染热销商品 (构建时生成 Top 20)
 *
 * 服务端获取 SPU 数据，客户端 islands: SKU 选择器 + 加购按钮 + 评价区 + 店铺卡
 *
 * ══════════════════════════════════════════════════════════════════
 * 2026-10-18 批次 B8 变更（对齐后端 `265543d6`）
 * ══════════════════════════════════════════════════════════════════
 * 1. **路由目录改名**：`app/product/[id]/` → **`app/spu/[spuId]/`**
 *    （资源段与路径变量口径：ADR-062 §决策 2/3、契约 §P2）。
 * 2. **路径变量 = 业务键**：`spuId` 现在是 **SPU 业务键 32-hex `String`**
 *    （`SpuVO.spu_id`），不再是技术键 `Long` ——「回链商品详情」因此恢复。
 * 3. **⚠️ 必删 `Number(id)`**：旧代码 `const { id } = await params` 后写
 *    `<ReviewsSection spuTechnicalId={Number(id)} />` —— 32-hex 走 `Number()` 得 **`NaN`**
 *    ⇒ 评价区请求 `/api/item/review/spu/NaN/filter` **整体失效**（不报错，只显空态）。
 *    现在**直接透传 `String`**，不做任何数值转换。
 * 4. **P2 的 404 语义**：`GET /api/item/spu/{spuId}` 的「不存在/已删除」是
 *    **真 HTTP 404 + `NO_RESOURCE_FOUND(1103)`**（不再是 `200 + code=3011`）⇒
 *    本页按 `isNotFound()` 分支落到 **`notFound()`**（段级 `not-found.tsx` 渲染
 *    「商品不存在或已删除」并返回真实 404 状态），**不再**与「暂时无法加载」混为一体；
 *    其余错误继续上抛 `error.tsx`（可重试）。
 * 5. **回链**：相关推荐卡用 `spu_id` 拼 `/spu/{spu_id}`（走 `spuHref()` 唯一出口，
 *    缺失业务键时不渲染链接）。
 */

import { notFound } from 'next/navigation';
import { getProduct, getProducts, isNotFound } from '@icedmall/api';
import type { ProductVO } from '@icedmall/api';
import { ProductCard, ImageGallery, SectionReveal } from '@icedmall/ui';
import { spuHref } from '@icedmall/utils';
import { AddToCart } from './add-to-cart';
import { ReviewsSection } from './reviews-section';
import { ShopInfoCard } from './shop-info-card';
import type { Metadata } from 'next';

export const revalidate = 120;

/* ================================================================
   generateStaticParams — 构建时预渲染热销商品
   ================================================================ */
export async function generateStaticParams() {
  try {
    const data = await getProducts({ sort: 'sales', order: 'desc', size: 20 });
    // 预渲染的键必须是 **SPU 业务键**（`spu_id`），与路由 `[spuId]` 同口径 ——
    // 若仍用技术键 `id`，会预渲染出 `/spu/1001` 这类路径，后端 `requireWire` 直接 400。
    return (data?.records ?? [])
      .map((p) => p.spu_id)
      .filter((spuId): spuId is string => !!spuId)
      .map((spuId) => ({ spuId }));
  } catch {
    return [];
  }
}

/* ================================================================
   generateMetadata — 动态 SEO
   ================================================================ */
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { spuId } = await params;
  try {
    const product = await getProduct(spuId);
    return {
      title: product.name,
      description: product.description || `${product.name} — 冰美商城精选好物`,
      openGraph: {
        title: product.name,
        description: product.description || '',
        images: product.main_image ? [product.main_image] : [],
      },
    };
  } catch {
    // 404 与其它错误的 SEO 元信息一致即可（页面本体由 `notFound()` / `error.tsx` 决定）
    return { title: '商品详情' };
  }
}

interface Props {
  params: Promise<{ spuId: string }>;
}

export default async function SpuDetailPage({ params }: Props) {
  const { spuId } = await params;

  let loaded: ProductVO | null = null;
  try {
    loaded = await getProduct(spuId);
  } catch (e) {
    // ⚠️ **只把 404 转成「商品不存在」**（P2 新语义：真 404 + 1103）。
    // 其它失败（网关不可达 / 5xx / 超时）继续上抛 —— 那是「暂时无法加载 + 重试」，
    // 不是「商品不存在」；混为一谈会让临时故障看起来像永久下架。
    if (isNotFound(e as { code?: number })) notFound();
    throw e;
  }

  if (!loaded) notFound();
  // 收窄为 const，闭包里（相关推荐的 filter）也能安全使用
  const product: ProductVO = loaded;

  // 相关推荐 — 同类目商品
  let relatedProducts: ProductVO[] = [];
  try {
    const related = await getProducts({
      categoryId: product.category_id,
      size: 4,
      sort: 'sales',
      order: 'desc',
    });
    // 排除自身：**必须用 SPU 业务键比业务键**（路由参数 `[spuId]` 就是 `SpuVO.spu_id`）。
    // 历史坑（勿重犯）：旧代码写 `p.product_id !== id` —— `product_id` 在报文里不存在
    // ⇒ 恒 `undefined` ⇒ 自我排除从未生效（多推一排「你自己」）。
    relatedProducts = related?.records?.filter((p) => p.spu_id !== product.spu_id) ?? [];
  } catch {
    relatedProducts = [];
  }

  const images = product.main_image ? [product.main_image] : [];

  return (
    <div className="mx-auto max-w-7xl px-4 pt-24 pb-20">
      {/* ── 主内容: 图片 + 信息 ── */}
      <div className="grid gap-10 md:grid-cols-2">
        {/* 左: 图片 */}
        <ImageGallery images={images} alt={product.name} />

        {/* 右: 商品信息 */}
        <div>
          {product.brand && (
            <p className="text-xs tracking-[0.2em] text-accent-gold uppercase">{product.brand}</p>
          )}
          <h1 className="mt-2 text-2xl md:text-3xl font-light text-ink-black leading-tight">
            {product.name}
          </h1>
          {product.description && (
            <p className="mt-4 text-sm text-warm-600 leading-relaxed">{product.description}</p>
          )}

          <AddToCart product={product} />

          {/* 店铺信息卡片 — 京东标准 */}
          {product.seller_id && (
            <div className="mt-6">
              <ShopInfoCard sellerId={product.seller_id} />
            </div>
          )}

          <div className="mt-8 flex items-center gap-6 text-xs text-warm-400">
            <span className="flex items-center gap-1">✓ 品质保证</span>
            <span className="flex items-center gap-1">✓ 7天退换</span>
            <span className="flex items-center gap-1">✓ 安全支付</span>
          </div>
        </div>
      </div>

      {/* ── 商品评价 ── */}
      {/* 路径变量与 VO 字段同型同义（都是 32-hex 业务键）—— **直接透传 String，不 Number()** */}
      <ReviewsSection spuId={product.spu_id ?? spuId} />

      {/* ── 相关推荐 ── */}
      {relatedProducts.length > 0 && (
        <SectionReveal>
          <div className="mt-24 border-t border-warm-200 pt-16">
            <h2 className="text-xl font-medium text-ink-black">你可能也喜欢</h2>
            <div className="mt-6 grid grid-cols-2 md:grid-cols-4 gap-6">
              {relatedProducts.map((rel) => (
                <ProductCard
                  key={rel.id}
                  product={rel}
                  variant="glass"
                  // `spuHref` 缺失业务键时返回 null → 传 undefined ⇒ 卡片不渲染链接
                  href={spuHref(rel.spu_id) ?? undefined}
                />
              ))}
            </div>
          </div>
        </SectionReveal>
      )}
    </div>
  );
}
