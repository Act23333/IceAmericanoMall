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

import { HeroScene } from './components/hero-scene';
import { BrandPhilosophy } from './components/brand-philosophy';
import { CuratedShowcase } from './components/curated-showcase';
import { CreatorSpace } from './components/creator-space';
import { MarketplacePortal } from './components/marketplace-portal';

export default function HomePage() {
  return (
    <main>
      <HeroScene />
      <BrandPhilosophy />
      <CuratedShowcase />
      <CreatorSpace />
      <MarketplacePortal />
    </main>
  );
}
