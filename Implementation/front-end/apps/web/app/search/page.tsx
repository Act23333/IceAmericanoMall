'use client';

import { useState, useEffect, useDeferredValue } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { ProductCard, SearchBar, SectionReveal } from '@icedmall/ui';

const API = process.env.NEXT_PUBLIC_API_URL || '';

interface Product { id: number; productId: string; categoryId: number; name: string; description?: string; brand: string; mainImage: string; price: number; soldCount: number; skus?: { price: number }[]; }
interface PageData { records: Product[]; total: number; pages: number; current: number; }

/**
 * 搜索页 — 原生 fetch, 无 TanStack Query 依赖
 */
export default function SearchPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const deferredKeyword = useDeferredValue(keyword);
  const [categoryId, setCategoryId] = useState<number | undefined>(
    searchParams.get('categoryId') ? Number(searchParams.get('categoryId')) : undefined
  );
  const [page, setPage] = useState(1);
  const [data, setData] = useState<PageData | null>(null);
  const [categories, setCategories] = useState<{ id: number; name: string }[]>([]);
  const [hotKeywords, setHotKeywords] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  // 加载搜索数据
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);

    const sp = new URLSearchParams({ page: String(page), size: '20' });
    if (deferredKeyword) sp.set('keyword', deferredKeyword);
    if (categoryId) sp.set('categoryId', String(categoryId));

    fetch(`${API}/api/search/product?${sp.toString()}`)
      .then((r) => r.json())
      .then((json) => { if (!cancelled) setData(json.data); })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [deferredKeyword, categoryId, page]);

  // 加载分类（仅一次）
  useEffect(() => {
    fetch(`${API}/api/item/category/tree`)
      .then((r) => r.json())
      .then((json) => setCategories(json.data || []))
      .catch(() => {});
  }, []);

  // 加载热门关键词（仅一次）
  useEffect(() => {
    fetch(`${API}/api/search/hot?limit=8`)
      .then((r) => r.json())
      .then((json) => setHotKeywords(json.data || []))
      .catch(() => {});
  }, []);

  // URL 同步
  useEffect(() => {
    const sp = new URLSearchParams();
    if (deferredKeyword) sp.set('keyword', deferredKeyword);
    if (categoryId) sp.set('categoryId', String(categoryId));
    if (page > 1) sp.set('page', String(page));
    const qs = sp.toString();
    router.replace(`/search${qs ? '?' + qs : ''}`, { scroll: false });
  }, [deferredKeyword, categoryId, page, router]);

  const totalPages = data?.pages ?? 0;

  return (
    <div className="mx-auto max-w-7xl px-4 pt-24 pb-20">
      <div className="max-w-xl mx-auto mb-6">
        <SearchBar placeholder="搜索商品、品牌、分类…" onSearch={(kw) => { setKeyword(kw); setPage(1); setCategoryId(undefined); }} />
      </div>

      {/* 热门搜索词 */}
      {hotKeywords.length > 0 && (
        <div className="mb-6 flex flex-wrap items-center gap-2 justify-center">
          <span className="text-xs text-warm-400">热门:</span>
          {hotKeywords.map((kw) => (
            <button key={kw} onClick={() => { setKeyword(kw); setPage(1); }}
              className="px-3 py-1 text-xs rounded-full border border-warm-200 text-warm-600 hover:border-accent-green hover:text-accent-green transition-colors bg-white">{kw}</button>
          ))}
        </div>
      )}

      {/* 分类筛选 */}
      {categories.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-6">
          <button onClick={() => { setCategoryId(undefined); setPage(1); }}
            className={`px-3 py-1.5 text-xs rounded-full border transition-all ${!categoryId ? 'border-accent-gold bg-accent-gold/5 text-ink-black' : 'border-warm-200 text-warm-600 hover:border-warm-400'}`}>全部分类</button>
          {categories.map((cat) => (
            <button key={cat.id} onClick={() => { setCategoryId(cat.id); setPage(1); }}
              className={`px-3 py-1.5 text-xs rounded-full border transition-all ${categoryId === cat.id ? 'border-accent-gold bg-accent-gold/5 text-ink-black' : 'border-warm-200 text-warm-600 hover:border-warm-400'}`}>{cat.name}</button>
          ))}
        </div>
      )}

      {data && <p className="text-sm text-warm-600 mb-4">{keyword ? `"${keyword}"` : '全部'} — 共 {data.total} 件</p>}

      {/* Loading */}
      {loading && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (<div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />))}
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="text-center py-20">
          <p className="text-warm-600">搜索服务暂不可用</p>
          <button onClick={() => setPage((p) => p)} className="mt-3 text-sm text-accent-green hover:underline">重试</button>
        </div>
      )}

      {/* Results */}
      {!loading && !error && data && data.records.length > 0 && (
        <SectionReveal><div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {data.records.map((p) => (<ProductCard key={p.id} product={p} href={`/product/${p.id}`} />))}
        </div></SectionReveal>
      )}

      {!loading && !error && data && data.records.length === 0 && (
        <div className="text-center py-20">
          <div className="text-6xl select-none">🍃</div>
          <h2 className="mt-4 text-lg font-medium text-ink-black">没有找到相关商品</h2>
          <p className="mt-2 text-sm text-warm-600">试试其他关键词</p>
          <button onClick={() => { setKeyword(''); setCategoryId(undefined); }} className="mt-4 text-sm text-accent-green hover:underline">清除筛选</button>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-8">
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 disabled:opacity-30">上一页</button>
          <span className="px-3 py-1.5 text-sm text-warm-600">{page}/{totalPages}</span>
          <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 disabled:opacity-30">下一页</button>
        </div>
      )}
    </div>
  );
}
