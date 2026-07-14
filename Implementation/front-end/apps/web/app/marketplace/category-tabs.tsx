'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import type { CategoryVO } from '@icedmall/api';

export function CategoryTabs({ categories }: { categories: CategoryVO[] }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const activeId = searchParams.get('categoryId');

  const navigate = (id: number | null) => {
    if (id === null) {
      router.push('/marketplace', { scroll: false });
    } else {
      router.push(`/marketplace?categoryId=${id}`, { scroll: false });
    }
  };

  return (
    <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-hide">
      <button onClick={() => navigate(null)}
        className={`shrink-0 px-4 py-2 text-sm rounded-full border transition-all ${
          !activeId ? 'border-accent-green bg-accent-green text-white' : 'border-warm-200 text-warm-600 hover:border-warm-400 bg-white'
        }`}>全部</button>
      {categories.map((cat) => (
        <button key={cat.id} onClick={() => navigate(cat.id)}
          className={`shrink-0 px-4 py-2 text-sm rounded-full border transition-all ${
            String(cat.id) === activeId ? 'border-accent-green bg-accent-green text-white' : 'border-warm-200 text-warm-600 hover:border-warm-400 bg-white/80 backdrop-blur-sm hover:shadow-sm'
          }`}>{cat.name}</button>
      ))}
    </div>
  );
}
