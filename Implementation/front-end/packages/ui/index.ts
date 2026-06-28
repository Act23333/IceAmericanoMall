/**
 * @icedmall/ui — 共享 UI 组件
 *
 * 提供：
 * 1. cn() — 合并 className（tailwind-merge + clsx）
 * 2. 通用组件 — 遵循「东方自然主义 × 未来玻璃艺术」设计语言
 *
 * 注意：Shadcn/ui 组件通过 `npx shadcn-ui add` 复制到此包中
 */

// 工具
export { cn } from './lib/utils';

// 设计系统组件
export { Button } from './components/button';
export { GlassCard } from './components/glass-card';
export { PriceDisplay } from './components/price-display';

// 业务组件
export { ProductCard } from './components/product-card';
export { SearchBar } from './components/search-bar';
export { SectionReveal } from './components/section-reveal';

// 商城组件
export { Footer } from './components/footer';
export { CategoryCard } from './components/category-card';
export { HorizontalScroll } from './components/horizontal-scroll';
export { SkuSelector } from './components/sku-selector';
export { ImageGallery } from './components/image-gallery';
