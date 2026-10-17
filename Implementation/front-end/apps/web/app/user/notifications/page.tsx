'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient, type UnreadCountVO } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import {
  Heart,
  MessageSquare,
  UserPlus,
  Package,
  CreditCard,
  RotateCcw,
  ShieldCheck,
  ReceiptText,
  Bell,
  Check,
  type LucideIcon,
} from 'lucide-react';
import Link from 'next/link';
import { timeAgo } from '@icedmall/utils';
import {
  notificationTabByKey,
  notificationTypeLabel,
  NOTIFICATION_TAB_ALL,
  NOTIFICATION_TABS,
  type NotificationTab,
  type NotificationType,
} from '@icedmall/utils';
import { LoadFailedState } from '@/app/components/load-failed-state';
import { useNotificationPage, NOTIFICATION_PAGE_SIZE } from './use-notification-page';

/**
 * 消息中心 — notification-service（`/api/notifications/**`）。
 *
 * ── 2026-10 B10：标签页取值域更正（**这是一次静默失效的修复**）──────────────
 * 旧实现照抄 `notification` 表那条**过时**列注释（`LIKE/REPLY/APPEND/FOLLOW/ORDER/SYSTEM`），
 * 开了 点赞/回复/关注/**订单** 4 个标签页。后端真源（4 个生产方常量，见
 * `packages/utils/notification-type.ts` 文件头表）里：
 *   - `ORDER` **从不产生** ⇒ 「订单」页恒空；
 *   - 实际在产的 `PAY_SUCCESS / REFUND_SUCCESS / ORDER_SHIPPED / AFTER_SALE_REVIEWED /
 *     INVOICE_ISSUED / INVOICE_RED_FLUSHED / INVOICE_VOIDED` 一个标签页都没有。
 *
 * 现在的口径：标签页 = `NOTIFICATION_TABS`（全部 / 互动 / 交易 / 售后 / 发票 / 其他），
 * **每个后端取值都落在某一页**（编译期断言 `NOTIFICATION_TAB_COVERAGE_ASSERT` 兜住未来新增值），
 * 未知取值落「其他」页且文案回落**原始值**（`notificationTypeLabel`），**不丢弃**。
 *
 * ── 其它已收口的口径 ──────────────────────────────────────────
 * ✅ 路径 `/api/notifications/**`（不是 `/api/user/notifications*`）
 * ✅ 分页参数 `page_num` / `page_size`（线上名一律 snake_case）
 * ✅ 未读计数响应 `{"unread": n}`（不是 `.count`）
 * ✅ **失败态**：旧实现只判 `isLoading` + `records.length`，请求失败渲染成「暂无消息」，
 *    与「真的没有通知」不可分辨；现在 `isError` 单独走 `LoadFailedState`（语义色 + 重试）。
 */

/**
 * type → 图标/配色（`Record<NotificationType, …>` ⇒ 后端取值漏挂即**编译期报错**）。
 * 这是继 `NOTIFICATION_TABS` 之后的第二道完整性守卫。
 */
const TYPE_FACE: Record<NotificationType, { icon: LucideIcon; color: string }> = {
  LIKE: { icon: Heart, color: 'text-danger' },
  REPLY: { icon: MessageSquare, color: 'text-accent' },
  FOLLOW: { icon: UserPlus, color: 'text-info' },
  ORDER_SHIPPED: { icon: Package, color: 'text-amber' },
  PAY_SUCCESS: { icon: CreditCard, color: 'text-accent' },
  REFUND_SUCCESS: { icon: RotateCcw, color: 'text-info' },
  AFTER_SALE_REVIEWED: { icon: ShieldCheck, color: 'text-info' },
  INVOICE_ISSUED: { icon: ReceiptText, color: 'text-accent' },
  INVOICE_RED_FLUSHED: { icon: ReceiptText, color: 'text-danger' },
  INVOICE_VOIDED: { icon: ReceiptText, color: 'text-text-tertiary' },
};

/** 未知取值（后端新增 type / 脏数据）的中性图标 —— 分类落「其他」，记录本身照常展示。 */
const UNKNOWN_TYPE_FACE = { icon: Bell, color: 'text-text-secondary' };

export default function NotificationsPage() {
  const [tabKey, setTabKey] = useState<string>(NOTIFICATION_TAB_ALL);
  const [page, setPage] = useState(1);
  const qc = useQueryClient();

  const tab: NotificationTab = notificationTabByKey(tabKey);
  const {
    records, pages, total, approximate,
    isLoading, isError, error, isRefetching, refetch,
  } = useNotificationPage(tab, page);

  const { data: unread } = useQuery<UnreadCountVO>({
    queryKey: ['notifications', 'unread'],
    queryFn: () => apiClient<UnreadCountVO>('/api/notifications/unread-count'),
    refetchInterval: 30_000,
  });

  const markAllRead = useMutation({
    mutationFn: () => apiClient<void>('/api/notifications/read-all', { method: 'PUT' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['notifications'] }); },
  });

  const markRead = useMutation({
    mutationFn: (id: number) => apiClient<void>(`/api/notifications/${id}/read`, { method: 'PUT' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['notifications', tabKey, page] }); qc.invalidateQueries({ queryKey: ['notifications', 'unread'] }); },
  });

  const switchTab = (key: string) => { setTabKey(key); setPage(1); };

  return (
    <div className="mx-auto max-w-2xl px-4 pt-8 pb-20">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-2">
          <h1 className="text-xl font-light text-ink-black">消息中心</h1>
          {(unread?.unread ?? 0) > 0 && (
            <span className="px-2 py-0.5 text-xs rounded-full bg-danger text-white">{unread?.unread}</span>
          )}
        </div>
        {(unread?.unread ?? 0) > 0 && (
          <Button variant="ghost" size="sm" onClick={() => markAllRead.mutate()}>
            <Check className="h-4 w-4 mr-1" />全部已读
          </Button>
        )}
      </div>

      {/* Tabs —— 与后端 type 取值域逐值对应（见 NOTIFICATION_TABS） */}
      <div className="flex gap-2 mb-4 overflow-x-auto pb-1">
        {NOTIFICATION_TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => switchTab(t.key)}
            aria-pressed={tabKey === t.key}
            className={`shrink-0 px-4 py-1.5 text-sm rounded-full border transition-colors ${
              tabKey === t.key
                ? 'border-accent bg-accent/5 text-accent'
                : 'border-warm-gray-200 text-text-tertiary hover:border-warm-gray-400'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* 「其他」页的口径说明 —— 近似分页必须写出来，不伪装成精确结果 */}
      {approximate && (
        <p className="mb-3 text-xs text-text-tertiary">
          本页收录后端新增、前端尚未归类（当前标签页之外）的通知类型；分类口径按全部消息的分页推进。
        </p>
      )}

      {/* ── 失败态（与空态视觉/文案可分 + 重试）── */}
      {isError && (
        <LoadFailedState
          title="消息加载失败"
          description="没能获取到通知列表（不是「没有消息」）。请重试。"
          detail={error instanceof Error ? error.message : undefined}
          onRetry={refetch}
          retrying={isRefetching}
        />
      )}

      {/* ── 加载中 ── */}
      {isLoading && !isError && (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-16 rounded-xl bg-warm-gray-100 animate-pulse" />
          ))}
        </div>
      )}

      {/* ── 空态（只有"确实没有数据"才走这里）── */}
      {!isLoading && !isError && records.length === 0 && (
        <div className="text-center py-16">
          <Bell className="mx-auto h-10 w-10 text-warm-gray-400" />
          <p className="mt-3 text-sm text-text-secondary">
            {tabKey === NOTIFICATION_TAB_ALL ? '暂无消息' : `暂无「${tab.label}」类消息`}
            {approximate && '（本页无其他类型通知）'}
          </p>
        </div>
      )}

      {/* ── 列表 ── */}
      {!isError && (
        <div className="space-y-2">
          {records.map((n) => {
            const face = (n.type && TYPE_FACE[n.type as NotificationType]) || UNKNOWN_TYPE_FACE;
            const Icon = face.icon;
            return (
              <Link key={n.id} href={n.link_url || '#'} onClick={() => { if (n.is_read === 0) markRead.mutate(n.id); }}>
                <GlassCard className={`flex items-start gap-3 p-3 transition-opacity ${n.is_read === 1 ? 'opacity-60' : ''}`} blur="sm">
                  <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${n.is_read === 0 ? 'bg-accent/10' : 'bg-warm-gray-100'}`}>
                    <Icon className={`h-4 w-4 ${face.color}`} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <p className={`text-sm ${n.is_read === 0 ? 'font-medium text-ink-black' : 'text-text-secondary'}`}>
                        {n.title}
                      </p>
                      {n.is_read === 0 && <span className="h-2 w-2 rounded-full bg-accent shrink-0" />}
                    </div>
                    {n.content && (
                      <p className="text-xs text-text-tertiary mt-0.5 line-clamp-1">{n.content}</p>
                    )}
                    <p className="text-xs text-text-tertiary mt-1">
                      {/* 类型文案唯一出口：未知取值回落**原始值**（不空白、不写"未知"吞掉） */}
                      <span className="mr-1.5">{notificationTypeLabel(n.type)}</span>
                      {n.sender_name && <span>· {n.sender_name} </span>}
                      {n.create_time ? <span>· {timeAgo(n.create_time)}</span> : null}
                    </p>
                  </div>
                </GlassCard>
              </Link>
            );
          })}
        </div>
      )}

      {/* ── 分页 ── */}
      {!isError && pages > 1 && (
        <div className="flex justify-center gap-2 mt-6">
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-gray-200 disabled:opacity-30">上一页</button>
          <span className="px-3 py-1.5 text-sm text-text-secondary">
            {page}/{pages}{approximate ? '（按全部）' : ''}
          </span>
          <button onClick={() => setPage((p) => Math.min(pages, p + 1))} disabled={page >= pages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-gray-200 disabled:opacity-30">下一页</button>
        </div>
      )}

      {/* 当前页统计（便于核对「标签页恒空」类问题：条数与总数都摊开） */}
      {!isError && !isLoading && records.length > 0 && (
        <p className="mt-4 text-center text-xs text-text-tertiary">
          本页 {records.length} 条 · 共 {total} 条 · 每页 {NOTIFICATION_PAGE_SIZE} 条
        </p>
      )}
    </div>
  );
}
