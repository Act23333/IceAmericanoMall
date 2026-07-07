/**
 * 商品详情页
 *
 * ISR: 120s 重新生成
 * generateStaticParams: 预渲染热销商品 (构建时生成 Top 20)
 *
 * 服务端获取商品数据，客户端 islands: SKU 选择器 + 加购按钮
 */

import { getProduct, getProducts } from '@icedmall/api';
import { ProductCard, ImageGallery, PriceDisplay, SectionReveal } from '@icedmall/ui';
import { AddToCart } from './add-to-cart';
import type { Metadata } from 'next';

export const revalidate = 120;

/* ================================================================
   generateStaticParams — 构建时预渲染热销商品
   ================================================================ */
export async function generateStaticParams() {
  try {
    const data = await getProducts({ sort: 'sales', order: 'desc', size: 20 });
    return (data?.records ?? []).map((p) => ({ id: String(p.id) }));  // 后端 Long id
  } catch {
    return [];
  }
}

/* ================================================================
   generateMetadata — 动态 SEO
   ================================================================ */
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  try {
    const product = await getProduct(id);
    return {
      title: product.name,
      description: product.description || `${product.name} — 冰美商城精选好物`,
      openGraph: {
        title: product.name,
        description: product.description || '',
        images: product.mainImage ? [product.mainImage] : [],
      },
    };
  } catch {
    return { title: '商品详情' };
  }
}

interface Props {
  params: Promise<{ id: string }>;
}

export default async function ProductDetailPage({ params }: Props) {
  const { id } = await params;

  let product;
  try {
    product = await getProduct(id);
  } catch {
    return <NotFound productId={id} />;
  }

  if (!product) {
    return <NotFound productId={id} />;
  }

  // 相关推荐 — 同类目商品
  let relatedProducts;
  try {
    const related = await getProducts({
      categoryId: product.categoryId,
      size: 4,
      sort: 'sales',
      order: 'desc',
    });
    relatedProducts = related?.records?.filter((p) => p.productId !== id) ?? [];
  } catch {
    relatedProducts = [];
  }

  const firstSku = product.skus?.[0];
  const images = product.mainImage ? [product.mainImage] : [];

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

          <div className="mt-6">
            <PriceDisplay cents={firstSku?.price ?? 0} size="lg" />
            {product.soldCount > 0 && (
              <p className="mt-1 text-xs text-warm-400">
                已售 {product.soldCount > 10000 ? `${(product.soldCount / 10000).toFixed(1)}万` : product.soldCount}
              </p>
            )}
          </div>

          <AddToCart product={product} />

          <div className="mt-8 flex items-center gap-6 text-xs text-warm-400">
            <span className="flex items-center gap-1">✓ 品质保证</span>
            <span className="flex items-center gap-1">✓ 7天退换</span>
            <span className="flex items-center gap-1">✓ 安全支付</span>
          </div>
        </div>
      </div>

      {/* ── 相关推荐 ── */}
      {relatedProducts.length > 0 && (
        <SectionReveal>
          <div className="mt-24 border-t border-warm-200 pt-16">
            <h2 className="text-xl font-medium text-ink-black">你可能也喜欢</h2>
            <div className="mt-6 grid grid-cols-2 md:grid-cols-4 gap-6">
              {relatedProducts.map((rel) => (
                <ProductCard
                  key={rel.productId}
                  product={rel}
                  variant="glass"
                  href={`/product/${rel.productId}`}
                />
              ))}
            </div>
          </div>
        </SectionReveal>
      )}
    </div>
  );
}

function NotFound({ productId }: { productId: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-center">
        <div className="text-6xl select-none">🔍</div>
        <h2 className="mt-4 text-xl font-medium text-ink-black">商品未找到</h2>
        <p className="mt-2 text-sm text-warm-600">商品 {productId} 可能已下架或不存在</p>
        <a href="/marketplace" className="mt-6 inline-block text-sm text-accent-green hover:underline">
          返回商城 →
        </a>
      </div>
    </div>
  );
}
