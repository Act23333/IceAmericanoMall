/**
 * 搜索页布局 — 复用商城导航+页脚
 */
export const dynamic = 'force-dynamic';

import { Footer } from '@icedmall/ui';
import { Navbar } from '@/app/components/navbar';

export default function SearchLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <>
      <Navbar />
      <main className="min-h-screen">{children}</main>
      <Footer />
    </>
  );
}
