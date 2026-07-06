export const dynamic = 'force-dynamic';

import { Suspense } from 'react';
import { Footer } from '@icedmall/ui';
import { Navbar } from '@/app/components/navbar';

export default function SearchLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Navbar />
      <main className="min-h-screen">
        <Suspense fallback={
          <div className="mx-auto max-w-7xl px-4 pt-24">
            <div className="h-10 max-w-xl mx-auto rounded-full bg-warm-100 animate-glass-shimmer mb-6" />
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
              ))}
            </div>
          </div>
        }>
          {children}
        </Suspense>
      </main>
      <Footer />
    </>
  );
}
