'use client';

import { useState } from 'react';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { apiClient, type PageResult, type HistoryVO } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import { Search, Trash2, Clock } from 'lucide-react';
import Link from 'next/link';

/**
 * 浏览足迹 —— 直接使用契约类型 `HistoryVO`（多词键名由后端 `@JsonProperty` 固定为 snake_case）。
 *
 * ⚠️ 本端点是 `spu_name` 收口的**唯一例外**（2026-10-17 B3 实测）：
 *   - `spu_id` —— 已收口（`product_id` → `spu_id`，**SPU 业务键 32-hex `String`**）；
 *   - `product_name` —— **线上键仍是 `product_name`**！后端 `HistoryVO.productName` 的
 *     `@JsonProperty("product_name")` 未随 B2 改名（B1 的 14 处 `spu_name` 清单不含足迹端点）。
 *     **不要**把它改成 `spu_name` —— 改了商品名会整体变 `undefined`。
 *
 * ⚠️ 回链商品详情的限制同收藏页：`HistoryVO` 只带 SPU **业务键**，而
 * `GET /api/item/product/{id}` 路径变量仍是**技术键 `Long`**（契约 §十二 12.2 R1 未收口）
 * ⇒ 旧代码 `href={`/product/${item.product_id}`}` 实为 `/product/undefined`。
 * 本页不再伪造深链（不显示任何 id）。
 */
type FootprintItem = HistoryVO;
type PageData = PageResult<HistoryVO>;

/** 日期分组 */
function groupByDate(items: FootprintItem[]): { label: string; items: FootprintItem[] }[] {
  const groups: Record<string, FootprintItem[]> = {};
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const yesterday = new Date(today.getTime() - 86400000);
  const weekAgo = new Date(today.getTime() - 7 * 86400000);

  items.forEach((item) => {
    const d = new Date(item.view_time);
    const day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    let key: string;
    if (day.getTime() === today.getTime()) key = '今天';
    else if (day.getTime() === yesterday.getTime()) key = '昨天';
    else if (day.getTime() > weekAgo.getTime()) key = '本周';
    else key = '更早';
    if (!groups[key]) groups[key] = [];
    groups[key]!.push(item);
  });

  const order = ['今天', '昨天', '本周', '更早'];
  return order.filter((k) => groups[k]).map((k) => ({ label: k, items: groups[k]! }));
}

function formatViewTime(t: string): string {
  const d = new Date(t);
  return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export default function HistoryPage() {
  const qc = useQueryClient();
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const { data, isLoading } = useQuery<PageData>({
    queryKey: ['history', page, keyword],
    queryFn: () => {
      const p = new URLSearchParams({ page: String(page), size: '20' });
      if (keyword) p.set('keyword', keyword);
      return apiClient<PageData>(`/api/user/history?${p.toString()}`);
    },
    staleTime: 30_000,
  });

  const clearMutation = useMutation({
    mutationFn: () => apiClient('/api/user/history', { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['history'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => apiClient(`/api/user/history/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['history'] }),
  });

  const handleSearch = () => {
    setKeyword(searchInput.trim());
    setPage(1);
  };

  const records = data?.records || [];
  const groups = groupByDate(records);
  const total = data?.total || 0;

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-light text-ink-black">我的足迹</h1>
        {total > 0 && (
          <Button size="sm" variant="ghost" onClick={() => { if (confirm('确定清空全部足迹？')) clearMutation.mutate(); }}>
            清空全部
          </Button>
        )}
      </div>

      {/* 搜索框 */}
      <div className="flex gap-2 mb-6">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-text-tertiary" />
          <input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            placeholder="搜索浏览过的商品..."
            className="w-full rounded-xl border border-warm-gray-200 bg-white pl-10 pr-4 py-2.5 text-sm outline-none focus:border-accent transition-colors"
          />
        </div>
        <Button variant="secondary" size="md" onClick={handleSearch}>搜索</Button>
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => <div key={i} className="h-16 rounded-xl bg-warm-gray-100 animate-pulse" />)}
        </div>
      )}

      {/* Empty */}
      {!isLoading && records.length === 0 && (
        <div className="text-center py-20">
          <div className="text-6xl select-none">👣</div>
          <p className="mt-4 text-text-secondary">
            {keyword ? '没有匹配的浏览记录' : '暂无浏览足迹'}
          </p>
          <Link href="/marketplace" className="mt-3 inline-block text-sm text-accent hover:underline">
            去逛逛 →
          </Link>
        </div>
      )}

      {/* 日期分组列表 */}
      {groups.map((group) => (
        <div key={group.label} className="mb-6">
          <h2 className="text-sm font-medium text-text-secondary mb-3">{group.label}</h2>
          <div className="space-y-2">
            {group.items.map((item) => (
              <GlassCard key={item.id} className="flex items-center gap-3 p-3 group" blur="sm">
                <div className="flex items-center gap-3 flex-1 min-w-0">
                  {item.main_image ? (
                    <img src={item.main_image} alt="" className="h-14 w-14 rounded-lg object-cover bg-warm-gray-100 shrink-0" />
                  ) : (
                    <div className="h-14 w-14 rounded-lg bg-warm-gray-100 shrink-0 flex items-center justify-center text-xl">📦</div>
                  )}
                  <div className="flex-1 min-w-0">
                    {/* 线上键 `product_name`（本端点的唯一保留项，见文件头说明） */}
                    <p className="text-sm text-ink-black truncate">{item.product_name}</p>
                    <div className="flex items-center gap-3 mt-1">
                      <span className="text-sm font-medium text-ink-black">¥{formatPrice(item.price ?? 0)}</span>
                      <span className="flex items-center gap-1 text-xs text-text-tertiary">
                        <Clock className="h-3 w-3" />
                        {formatViewTime(item.view_time)}
                      </span>
                    </div>
                  </div>
                </div>
                <button
                  onClick={() => deleteMutation.mutate(item.id)}
                  className="p-2 text-text-tertiary hover:text-danger transition-colors shrink-0 opacity-0 group-hover:opacity-100"
                  title="删除"
                  aria-label="删除该条足迹"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </GlassCard>
            ))}
          </div>
        </div>
      ))}

      {/* 分页 */}
      {data && data.pages > 1 && (
        <div className="flex justify-center gap-2 mt-8">
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-gray-200 disabled:opacity-30 hover:border-warm-gray-400 transition-colors">上一页</button>
          <span className="px-3 py-1.5 text-sm text-text-secondary">{page} / {data.pages}</span>
          <button onClick={() => setPage((p) => Math.min(data.pages, p + 1))} disabled={page >= data.pages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-gray-200 disabled:opacity-30 hover:border-warm-gray-400 transition-colors">下一页</button>
        </div>
      )}
    </div>
  );
}
