/**
 * 商城 Layout — 品牌主题 + 顶部导航 + 底部
 *
 * 数据范围: data-theme-scope="marketplace"
 * 渲染: ISR 60s
 */
export const revalidate = 60;

export default function MarketplaceLayout({ children }: { children: React.ReactNode }) {
  return (
    <div data-theme-scope="marketplace">
      {children}
    </div>
  );
}
