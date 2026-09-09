'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { apiClient, type FlashSaleEntity } from '@icedmall/api';

export function FlashSaleBanner() {
  const { data } = useQuery<FlashSaleEntity[]>({
    queryKey: ['flash-sales', 'active'],
    queryFn: () => apiClient<FlashSaleEntity[]>('/api/flash-sale/list'),
    staleTime: 30_000,
  });

  if (!data || data.length === 0) return null;

  return (
    <section className="py-8">
      <div className="bg-gradient-to-r from-red-50 to-orange-50 rounded-2xl p-6 border border-red-100">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-medium text-red-600">⚡ 限时秒杀</h2>
          <Link href="/marketplace?sort=sales&order=desc" className="text-xs text-red-400">更多 →</Link>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {data.slice(0, 4).map((fs) => (
            <Link key={fs.id} href={`/product/${fs.productId}`}
              className="bg-white rounded-xl p-3 text-center hover:shadow-md transition-shadow">
              <p className="text-xl font-bold text-red-500">¥{(fs.price / 100).toFixed(0)}</p>
              <p className="text-xs text-warm-400 mt-1 line-through">原价</p>
              <div className="mt-2 h-1.5 bg-warm-100 rounded-full overflow-hidden">
                <div className="h-full bg-red-400 rounded-full" style={{ width: `${Math.min(100, (fs.soldCount / fs.stock) * 100)}%` }} />
              </div>
              <p className="text-xs text-warm-400 mt-1">已售 {fs.soldCount}/{fs.stock}</p>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
