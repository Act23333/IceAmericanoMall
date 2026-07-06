/**
 * 商城布局 — 导航 + 内容 + 页脚
 */
export const dynamic = 'force-dynamic';

import { Suspense } from 'react';
import { Footer } from '@icedmall/ui';
import { Navbar } from '@/app/components/navbar';

export default function MarketplaceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <>
      <Navbar />
      <main className="min-h-screen pt-16">
        <Suspense fallback={
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-8 px-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
            ))}
          </div>
        }>
          {children}
        </Suspense>
      </main>
      <Footer />
    </>
  );
}
