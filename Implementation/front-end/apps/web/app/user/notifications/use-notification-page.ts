'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient, type PageResult, type InAppMessageVO } from '@icedmall/api';
import {
  isAllNotificationTab,
  isFallbackNotificationTab,
  isKnownNotificationType,
  warnUnknownNotificationTypes,
  type NotificationTab,
} from '@icedmall/utils';

/**
 * 通知中心的分页取数 —— 把「标签页 → 服务端查询」的口径收敛在一处。
 *
 * ⚠️ **服务端只支持单值精确筛选**（`GET /api/notifications?type=`，后端
 * `InAppMessageRepositoryImpl.pageByRecipient` 是 `wrapper.eq(type)`）。
 * 因此：
 *   - 「全部」→ 不带 `type`；
 *   - 单值标签页 → 精确带上该值；
 *   - **多值标签页**（互动 / 交易 / 发票）→ 逐值取数后合并，页码按
 *     「每个值取 `PAGE_SIZE × page` 条 → 合并排序 → 切片」推进。
 *     这是唯一不丢记录、也不串页的口径：第 p 页的前 `PAGE_SIZE×p` 条一定落在
 *     各数据源的各自前 `PAGE_SIZE×p` 条之内（并集前 N = 各源前 N 的并集）。
 *   - 「其他」→ 后端**无法**按「未知取值」筛选，故取全部再在客户端筛出
 *     `type ∉ 已知取值集合` 的记录；分页因此只能是「按全部页推进」的近似口径，界面已注明。
 */

/** 每页条数（与后端 `page_size` 默认 10 不同，这里显式声明）。 */
export const NOTIFICATION_PAGE_SIZE = 20;

interface NotificationPageData {
  records: InAppMessageVO[];
  /** 该标签页命中的总条数（多值分组 = 各值 total 之和）。 */
  total: number;
  /** 总页数。 */
  pages: number;
  /**
   * 是否为「近似口径」：仅「其他」页为 true —— 它按全部数据的分页推进，
   * 单页里可能一条未知类型都没有。界面必须把这点写出来，不能伪装成精确结果。
   */
  approximate: boolean;
}

export interface NotificationPageModel extends NotificationPageData {
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  isRefetching: boolean;
  /** 重试（失败态按钮用）。 */
  refetch: () => void;
}

async function fetchPage(type: string | null, page: number, size: number): Promise<PageResult<InAppMessageVO>> {
  const p = new URLSearchParams({ page_num: String(page), page_size: String(size) });
  if (type) p.set('type', type);
  return apiClient<PageResult<InAppMessageVO>>(`/api/notifications?${p.toString()}`);
}

/** 按创建时间倒序（`create_time` 为 `LocalDateTime` 序列化后的 ISO 串，字典序即时间序）。 */
function byCreateTimeDesc(a: InAppMessageVO, b: InAppMessageVO): number {
  return (b.create_time ?? '').localeCompare(a.create_time ?? '');
}

async function loadTabPage(tab: NotificationTab, page: number): Promise<NotificationPageData> {
  /* ① 「其他」兜底页 —— 未知取值只能客户端筛（取全部页） */
  if (isFallbackNotificationTab(tab)) {
    const raw = await fetchPage(null, page, NOTIFICATION_PAGE_SIZE);
    warnUnknownNotificationTypes(raw.records);
    return {
      records: raw.records.filter((r) => !isKnownNotificationType(r.type)),
      total: raw.total,
      pages: raw.pages ?? 0,
      approximate: true,
    };
  }

  /* ② 「全部」页 —— 不筛选 */
  if (isAllNotificationTab(tab)) {
    const raw = await fetchPage(null, page, NOTIFICATION_PAGE_SIZE);
    warnUnknownNotificationTypes(raw.records);
    return { records: raw.records, total: raw.total, pages: raw.pages ?? 0, approximate: false };
  }

  /* ③ 单值标签页 —— 服务端精确筛选（最省、最准） */
  const single = tab.types.length === 1 ? tab.types[0] : undefined;
  if (single) {
    const raw = await fetchPage(single, page, NOTIFICATION_PAGE_SIZE);
    warnUnknownNotificationTypes(raw.records);
    return { records: raw.records, total: raw.total, pages: raw.pages ?? 0, approximate: false };
  }

  /* ④ 多值标签页 —— 逐值取前 PAGE_SIZE×page 条，合并排序后切片 */
  const mergeSize = NOTIFICATION_PAGE_SIZE * page;
  const results = await Promise.all(tab.types.map((t) => fetchPage(t, 1, mergeSize)));
  const merged = results
    .flatMap((r) => r.records)
    .sort(byCreateTimeDesc);
  warnUnknownNotificationTypes(merged);
  const total = results.reduce((sum, r) => sum + (r.total ?? 0), 0);
  const from = (page - 1) * NOTIFICATION_PAGE_SIZE;
  return {
    records: merged.slice(from, from + NOTIFICATION_PAGE_SIZE),
    total,
    pages: Math.max(1, Math.ceil(total / NOTIFICATION_PAGE_SIZE)),
    approximate: false,
  };
}

/**
 * 通知中心页面数据（四态齐备：loading / 成功 / 空 / **失败**）。
 *
 * 失败态必须显式暴露 `isError` —— 旧实现只读 `isLoading` + `records.length`，
 * 请求失败被渲染成「暂无消息」，与「真的没有通知」不可分辨。
 */
export function useNotificationPage(tab: NotificationTab, page: number): NotificationPageModel {
  const query = useQuery<NotificationPageData>({
    queryKey: ['notifications', tab.key, page],
    queryFn: () => loadTabPage(tab, page),
  });

  return {
    records: query.data?.records ?? [],
    total: query.data?.total ?? 0,
    pages: query.data?.pages ?? 0,
    approximate: query.data?.approximate ?? false,
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
    isRefetching: query.isRefetching,
    refetch: () => { void query.refetch(); },
  };
}
