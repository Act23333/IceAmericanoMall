export const revalidate = 60;

import { Footer } from '@icedmall/ui';
import { Navbar } from '@/app/components/navbar';

export default function MarketplaceLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Navbar />
      <main className="min-h-screen pt-16">{children}</main>
      <Footer />
    </>
  );
}
