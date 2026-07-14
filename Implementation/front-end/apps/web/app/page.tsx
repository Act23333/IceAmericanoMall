/**
 * 冰美商城 — 品牌主页
 *
 * ISR: 每小时重新生成
 * 渲染策略: SSG + Client Islands（Hero 动画为客户端组件）
 *
 * 页面结构：
 *   1. Hero 场景 (全屏，昼夜变换)
 *   2. 品牌理念 (文本 + 留白)
 *   3. 精选策展 (3张品牌卡片)
 *   4. 创造者空间 (玻璃卡片)
 *   5. 进入商城 (入口 CTA)
 */
export const revalidate = 3600;
export const dynamic = 'force-static';

import { getProducts } from '@icedmall/api';
import { HeroScene } from './components/hero-scene';
import { BrandPhilosophy } from './components/brand-philosophy';
import { CuratedShowcase } from './components/curated-showcase';
import { CreatorSpace } from './components/creator-space';
import { MarketplacePortal } from './components/marketplace-portal';
import { ProductCard } from '@icedmall/ui';
import Link from 'next/link';

export default async function HomePage() {
  let products: any[] = [];
  try {
    const res = await getProducts({ sort: 'sales', order: 'desc', size: 8 });
    products = res?.records ?? [];
  } catch { /* static fallback */ }

  return (
    <main>
      <HeroScene />
      <BrandPhilosophy />
      <CuratedShowcase />
      <CreatorSpace />
      <MarketplacePortal />
      {/* 推荐商品 */}
      {products.length > 0 && (
        <section className="py-20 bg-mist-white">
          <div className="mx-auto max-w-7xl px-4">
            <h2 className="text-2xl font-light text-ink-black text-center mb-10">🔥 热销推荐</h2>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {products.map((p: any) => (
                <Link key={p.id} href={`/product/${p.id}`}>
                  <ProductCard product={p} variant="default" />
                </Link>
              ))}
            </div>
            <div className="text-center mt-8">
              <Link href="/marketplace" className="inline-block rounded-full border border-warm-300 px-6 py-2 text-sm text-ink-soft hover:border-accent-green hover:text-accent-green transition-colors">
                探索更多 →
              </Link>
            </div>
          </div>
        </section>
      )}
    </main>
  );
}
