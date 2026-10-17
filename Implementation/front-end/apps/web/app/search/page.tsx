'use client';

import { useState, useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { ProductCard, SearchBar } from '@icedmall/ui';
import { spuHref } from '@icedmall/utils';
import type { PageResult, ProductSearchVO, ProductVO, CategoryVO } from '@icedmall/api';
import { LoadFailedState } from '@/app/components/load-failed-state';

const API = process.env.NEXT_PUBLIC_API_URL || '';

/**
 * `GET /api/search/spu` 返回 `SearchPageVO<ProductSearchVO>`（信封字段与 IPage 同名）。
 * ⚠️ 资源段 `search/spu`（2026-10-18 B8）—— 旧 `search/product` 已随 ADR-062 §决策 2 废止。
 * 名字里带 `product` 的**非 SPU 段**不属本口径、一律照旧（实测：`/api/trade/seller/dashboard/product-stats`、
 * `/api/trade/admin/stats/top-products`）—— 别把它们一起改掉。
 */
type PageData = PageResult<ProductSearchVO>;

/** 关键词高亮 — 将搜索词拆分为单个字符和词组，红色标出 */
function HighlightName({ name, keyword }: { name: string; keyword: string }) {
  if (!keyword.trim()) return <>{name}</>;
  // 把关键词拆成独立词组(按空格) + 单字, 去重
  const parts = [...new Set([...keyword.split(/\s+/).filter(Boolean), ...keyword.replace(/\s+/g, '').split('')])];
  const pattern = parts.map((p) => p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
  if (!pattern) return <>{name}</>;

  const regex = new RegExp(`(${pattern})`, 'gi');
  const segments = name.split(regex);
  return (
    <>
      {segments.map((s, i) =>
        regex.test(s) ? (
          <mark key={i} className="bg-accent-gold/20 text-accent-green-dark rounded px-0.5">{s}</mark>
        ) : (
          <span key={i}>{s}</span>
        )
      )}
    </>
  );
}

/** 搜索历史组件 — 读取 localStorage */
function SearchHistory({ onSearch }: { onSearch: (kw: string) => void }) {
  const [history, setHistory] = useState<string[]>([]);
  useEffect(() => { setHistory(loadHistory()); }, []);
  if (history.length === 0) return null;
  return (
    <div className="mb-10">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-xs tracking-wider text-warm-400 font-medium">🕐 搜索历史</h3>
        <button onClick={() => { clearHistory(); setHistory([]); }} className="text-xs text-warm-400 hover:text-danger transition-colors">清空</button>
      </div>
      <div className="flex flex-wrap gap-2">
        {history.map((kw, i) => (
          <button key={i} onClick={() => onSearch(kw)}
            className="px-4 py-2 text-sm rounded-xl border border-warm-200 bg-white/70 backdrop-blur-sm text-ink-soft hover:border-accent-green/30 hover:text-accent-green hover:shadow-sm transition-all duration-200">{kw}</button>
        ))}
      </div>
    </div>
  );
}

/**
 * 搜索页 — 空状态展示推荐商品, 搜索后展示结果+分类筛选
 */
/** 本地搜索历史持久化 */
function loadHistory(): string[] { try { return JSON.parse(localStorage.getItem('search-history') || '[]'); } catch { return []; } }
function saveHistory(kw: string) {
  const h = [kw, ...loadHistory().filter((k) => k !== kw)].slice(0, 10);
  localStorage.setItem('search-history', JSON.stringify(h));
}
function clearHistory() { localStorage.removeItem('search-history'); }

export default function SearchPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const initialKeyword = searchParams.get('keyword') ?? '';

  const [keyword, setKeyword] = useState(initialKeyword);
  const [submitted, setSubmitted] = useState(!!initialKeyword);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [page, setPage] = useState(1);
  const [data, setData] = useState<PageData | null>(null);
  const [categories, setCategories] = useState<CategoryVO[]>([]);
  const [hotKeywords, setHotKeywords] = useState<string[]>([]);
  const [recommend, setRecommend] = useState<ProductVO[]>([]);
  const [loading, setLoading] = useState(false);
  /** 搜索结果失败原因（与「没有找到商品」严格区分 —— 旧实现把两者都渲染成空态）。 */
  const [resultError, setResultError] = useState<string | null>(null);
  /** 推荐位失败原因（首页商品流失败不能静默隐身）。 */
  const [recommendError, setRecommendError] = useState<string | null>(null);
  const [recommendLoading, setRecommendLoading] = useState(false);

  const loadRecommend = () => {
    setRecommendLoading(true);
    setRecommendError(null);
    fetch(`${API}/api/item/spu/page?sort=sales&order=desc&size=8`)
      .then((r) => r.json())
      .then((j) => {
        if (j.code === 200) setRecommend(j.data?.records || []);
        else setRecommendError(j.msg || `接口返回 ${j.code}`);
      })
      .catch((e: unknown) => setRecommendError(e instanceof Error ? e.message : '网络异常'))
      .finally(() => setRecommendLoading(false));
  };

  // 初始加载：热门词 + 推荐商品 + 分类 (仅一次)
  useEffect(() => {
    // 热门词 / 分类栏是**次级装饰区块**（2026-10 B10 判定）：失败即隐身，不宣称「暂无」，
    // 因此不会把「失败」说成「没有」。会宣称"没有"语义的两处（搜索结果、推荐商品）
    // 已各自带失败态 + 重试，见下方 `resultError` / `recommendError`。
    fetch(`${API}/api/search/hot?limit=8`).then((r) => r.json()).then((j) => setHotKeywords(j.data || [])).catch(() => {});
    loadRecommend();
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
    setResultError(null);

    // 后端 `SearchPageReq` 经 `@QueryModel` 绑定（Jackson + 全局 SNAKE_CASE）→ 线上名 `category_id`
    // （契约 §SE1）；camelCase `categoryId` 会被静默忽略 → 搜索结果页的品类筛选整体失效。
    const sp = new URLSearchParams({ page: String(pg), size: '20' });
    if (kw) sp.set('keyword', kw);
    if (catId) sp.set('category_id', String(catId));

    // 保存搜索历史
    if (kw) saveHistory(kw);

    fetch(`${API}/api/search/spu?${sp.toString()}`)
      .then((r) => r.json())
      .then((j) => {
        if (j.code === 200) {
          setData(j.data);
        } else {
          // ⚠️ 不得回落成 `{records: []}` —— 那会把「失败」伪装成「没有找到商品」
          setData(null);
          setResultError(j.msg || `接口返回 ${j.code}`);
        }
      })
      .catch((e: unknown) => {
        setData(null);
        setResultError(e instanceof Error ? e.message : '网络异常');
      })
      .finally(() => setLoading(false));

    // URL 同步 —— 本页路由自身的 query（应用内标识符，保持 camelCase；非后端线上名，勿混为一谈）
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
        <div className="max-w-2xl mx-auto mb-12">
          <SearchBar
            placeholder="搜索商品、品牌、分类…"
            hotKeywords={hotKeywords.length > 0 ? hotKeywords : undefined}
            history={loadHistory()}
            onSearch={handleSearch}
            onClearHistory={clearHistory}
          />
        </div>

        {/* 搜索历史 */}
        <SearchHistory onSearch={doSearch} />

        {/* 热门搜索 */}
        {hotKeywords.length > 0 && (
          <div className="mb-10">
            <h3 className="text-xs tracking-wider text-warm-400 mb-4 font-medium">🔥 热门搜索</h3>
            <div className="flex flex-wrap gap-2">
              {hotKeywords.map((kw) => (
                <button key={kw} onClick={() => doSearch(kw)}
                  className="px-4 py-2 text-sm rounded-xl border border-warm-200 bg-white/70 backdrop-blur-sm text-ink-soft hover:border-accent-green/30 hover:text-accent-green hover:shadow-sm transition-all duration-200">{kw}</button>
              ))}
            </div>
          </div>
        )}

        {/* 推荐商品 —— 失败必须可见（旧实现 `.catch(() => {})` ⇒ 整块凭空消失）*/}
        {recommendError && (
          <LoadFailedState
            compact
            title="推荐商品加载失败"
            description="没能获取到推荐商品（不是「没有推荐」）。"
            detail={recommendError}
            onRetry={loadRecommend}
            retrying={recommendLoading}
          />
        )}
        {!recommendError && recommend.length > 0 && (
          <div>
            <h3 className="text-xs tracking-wider text-warm-400 mb-4 font-medium">✨ 为你推荐</h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {recommend.map((p) => (
                // 回链 `/spu/{spu_id}`（业务键；缺 id 时 `spuHref` 返回 null ⇒ 不渲染链接）
                <ProductCard key={p.id} product={p} href={spuHref(p.spu_id) ?? undefined} />
              ))}
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
        <SearchBar
          placeholder="搜索商品、品牌、分类…"
          defaultValue={keyword}
          hotKeywords={hotKeywords.length > 0 ? hotKeywords : undefined}
          history={loadHistory()}
          onSearch={handleSearch}
          onClearHistory={clearHistory}
        />
      </div>

      {/* 分类筛选 (仅在搜索结果页显示) */}
      {categories.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-6">
          <button onClick={() => doSearch(keyword, undefined, 1)}
            className={`px-4 py-2 text-sm rounded-xl border transition-all duration-200 ${!categoryId ? 'border-accent-green bg-accent-green text-white shadow-sm' : 'border-warm-200 bg-white/80 backdrop-blur-sm text-warm-600 hover:border-warm-400 hover:shadow-sm'}`}>全部</button>
          {categories.map((cat) => (
            <button key={cat.id} onClick={() => doSearch(keyword, cat.id, 1)}
              className={`px-4 py-2 text-sm rounded-xl border transition-all duration-200 ${categoryId === cat.id ? 'border-accent-green bg-accent-green text-white shadow-sm' : 'border-warm-200 bg-white/80 backdrop-blur-sm text-warm-600 hover:border-warm-400 hover:shadow-sm'}`}>{cat.name}</button>
          ))}
        </div>
      )}

      {/* 结果计数 */}
      {data && !resultError && <p className="text-sm text-warm-600 mb-4">"{keyword}" — 共 {data.total} 件商品</p>}

      {/* Loading */}
      {loading && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (<div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />))}
        </div>
      )}

      {/* 失败态 —— 与「没有找到商品」严格区分，并给重试 */}
      {!loading && resultError && (
        <LoadFailedState
          title="搜索失败"
          description="没能取到搜索结果（不是「没有找到商品」）。请重试。"
          detail={resultError}
          onRetry={() => doSearch(keyword, categoryId, page)}
        />
      )}

      {/* Results */}
      {!loading && !resultError && data && data.records.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {data.records.map((p) => (
            <div key={p.id} className="relative">
              {/* 搜索结果 `SpuSearchVO` 带 `spu_id`（32-hex 业务键）→ 回链 `/spu/{spu_id}` */}
              <ProductCard product={p} href={spuHref(p.spu_id) ?? undefined} />
              {keyword && (
                <p className="mt-1 text-xs text-warm-500 px-1 truncate">
                  <HighlightName name={p.name} keyword={keyword} />
                </p>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Empty（只有"确实没有数据"才走这里）*/}
      {!loading && !resultError && data && data.records.length === 0 && (
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
