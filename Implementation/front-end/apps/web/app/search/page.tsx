'use client';

import { useState, useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { ProductCard, SearchBar } from '@icedmall/ui';

const API = process.env.NEXT_PUBLIC_API_URL || '';

interface Product { id: number; productId: string; categoryId: number; name: string; brand: string; mainImage: string; price: number; soldCount: number; }
interface PageData { records: Product[]; total: number; pages: number; }

/**
 * 搜索页 — 空状态展示推荐商品, 搜索后展示结果+分类筛选
 */
export default function SearchPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const initialKeyword = searchParams.get('keyword') ?? '';

  const [keyword, setKeyword] = useState(initialKeyword);
  const [submitted, setSubmitted] = useState(!!initialKeyword);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [page, setPage] = useState(1);
  const [data, setData] = useState<PageData | null>(null);
  const [categories, setCategories] = useState<{ id: number; name: string }[]>([]);
  const [hotKeywords, setHotKeywords] = useState<string[]>([]);
  const [recommend, setRecommend] = useState<Product[]>([]);
  const [loading, setLoading] = useState(false);

  // 初始加载：热门词 + 推荐商品 + 分类 (仅一次)
  useEffect(() => {
    fetch(`${API}/api/search/hot?limit=8`).then((r) => r.json()).then((j) => setHotKeywords(j.data || [])).catch(() => {});
    fetch(`${API}/api/item/product/page?sort=sales&order=desc&size=8`)
      .then((r) => r.json()).then((j) => setRecommend(j.data?.records || [])).catch(() => {});
    fetch(`${API}/api/item/category/tree`).then((r) => r.json()).then((j) => setCategories(j.data || [])).catch(() => {});
  }, []);

  // 如果 URL 已有 keyword, 自动搜索
  useEffect(() => {
    if (initialKeyword) doSearch(initialKeyword, undefined, 1);
  }, []);

  const doSearch = (kw: string, catId?: number, pg = 1) => {
    setKeyword(kw);
    setSubmitted(true);
    setCategoryId(catId);
    setPage(pg);
    setLoading(true);

    const sp = new URLSearchParams({ page: String(pg), size: '20' });
    if (kw) sp.set('keyword', kw);
    if (catId) sp.set('categoryId', String(catId));

    fetch(`${API}/api/search/product?${sp.toString()}`)
      .then((r) => r.json())
      .then((j) => setData(j.data))
      .catch(() => {})
      .finally(() => setLoading(false));

    // URL 同步
    const usp = new URLSearchParams();
    if (kw) usp.set('keyword', kw);
    if (catId) usp.set('categoryId', String(catId));
    router.replace(`/search${usp.toString() ? '?' + usp : ''}`, { scroll: false });
  };

  const handleSearch = (kw: string) => doSearch(kw.trim());

  const totalPages = data?.pages ?? 0;

  // ==================== 搜索前：推荐发现页 ====================
  if (!submitted) {
    return (
      <div className="mx-auto max-w-7xl px-4 pt-24 pb-20">
        {/* 搜索栏 */}
        <div className="max-w-xl mx-auto mb-10">
          <SearchBar placeholder="搜索商品、品牌、分类…" onSearch={handleSearch} />
        </div>

        {/* 热门搜索 */}
        {hotKeywords.length > 0 && (
          <div className="mb-10">
            <h3 className="text-sm font-medium text-warm-600 mb-3">🔥 热门搜索</h3>
            <div className="flex flex-wrap gap-2">
              {hotKeywords.map((kw) => (
                <button key={kw} onClick={() => doSearch(kw)}
                  className="px-4 py-1.5 text-sm rounded-full bg-warm-100 text-ink-soft hover:bg-accent-green/10 hover:text-accent-green transition-colors">{kw}</button>
              ))}
            </div>
          </div>
        )}

        {/* 推荐商品 */}
        {recommend.length > 0 && (
          <div>
            <h3 className="text-sm font-medium text-warm-600 mb-3">✨ 为你推荐</h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {recommend.map((p) => (<ProductCard key={p.id} product={p} href={`/product/${p.id}`} />))}
            </div>
          </div>
        )}
      </div>
    );
  }

  // ==================== 搜索后：结果页 ====================
  return (
    <div className="mx-auto max-w-7xl px-4 pt-24 pb-20">
      {/* 搜索栏 (保留当前关键词) */}
      <div className="max-w-xl mx-auto mb-6">
        <SearchBar placeholder="搜索商品、品牌、分类…" onSearch={handleSearch} />
      </div>

      {/* 分类筛选 (仅在搜索结果页显示) */}
      {categories.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-6">
          <button onClick={() => doSearch(keyword, undefined, 1)}
            className={`px-3 py-1.5 text-xs rounded-full border transition-all ${!categoryId ? 'border-accent-gold bg-accent-gold/5 text-ink-black' : 'border-warm-200 text-warm-600 hover:border-warm-400'}`}>全部分类</button>
          {categories.map((cat) => (
            <button key={cat.id} onClick={() => doSearch(keyword, cat.id, 1)}
              className={`px-3 py-1.5 text-xs rounded-full border transition-all ${categoryId === cat.id ? 'border-accent-gold bg-accent-gold/5 text-ink-black' : 'border-warm-200 text-warm-600 hover:border-warm-400'}`}>{cat.name}</button>
          ))}
        </div>
      )}

      {/* 结果计数 */}
      {data && <p className="text-sm text-warm-600 mb-4">"{keyword}" — 共 {data.total} 件商品</p>}

      {/* Loading */}
      {loading && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (<div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />))}
        </div>
      )}

      {/* Results */}
      {!loading && data && data.records.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {data.records.map((p) => (<ProductCard key={p.id} product={p} href={`/product/${p.id}`} />))}
        </div>
      )}

      {/* Empty */}
      {!loading && data && data.records.length === 0 && (
        <div className="text-center py-20">
          <div className="text-6xl select-none">🍃</div>
          <h2 className="mt-4 text-lg font-medium text-ink-black">没有找到 &quot;{keyword}&quot; 相关商品</h2>
          <p className="mt-2 text-sm text-warm-600">试试其他关键词</p>
          <button onClick={() => { setSubmitted(false); setKeyword(''); }}
            className="mt-4 text-sm text-accent-green hover:underline">返回推荐</button>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-8">
          <button onClick={() => doSearch(keyword, categoryId, page - 1)} disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 disabled:opacity-30">上一页</button>
          <span className="px-3 py-1.5 text-sm text-warm-600">{page}/{totalPages}</span>
          <button onClick={() => doSearch(keyword, categoryId, page + 1)} disabled={page >= totalPages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 disabled:opacity-30">下一页</button>
        </div>
      )}
    </div>
  );
}
