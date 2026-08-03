'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import { Heart, MessageSquare, UserPlus, Package, Bell, Check } from 'lucide-react';
import Link from 'next/link';
import { timeAgo } from '@icedmall/utils';

interface Notification {
  id: number;
  recipientId: number;
  senderId: number | null;
  senderName: string | null;
  senderAvatar: string | null;
  type: 'LIKE' | 'REPLY' | 'APPEND' | 'FOLLOW' | 'ORDER' | 'SYSTEM';
  title: string;
  content: string | null;
  linkUrl: string | null;
  isRead: number;
  createTime: string;
}

const TYPE_CONFIG: Record<string, { icon: any; color: string; label: string }> = {
  LIKE:    { icon: Heart,         color: 'text-danger',      label: '点赞' },
  REPLY:   { icon: MessageSquare, color: 'text-accent',      label: '回复' },
  APPEND:  { icon: MessageSquare, color: 'text-accent',      label: '追评' },
  FOLLOW:  { icon: UserPlus,      color: 'text-info',        label: '关注' },
  ORDER:   { icon: Package,       color: 'text-amber',       label: '订单' },
  SYSTEM:  { icon: Bell,          color: 'text-text-secondary', label: '系统' },
};

const TABS = [
  { label: '全部', value: null },
  { label: '点赞', value: 'LIKE' },
  { label: '回复', value: 'REPLY' },
  { label: '关注', value: 'FOLLOW' },
  { label: '订单', value: 'ORDER' },
];

export default function NotificationsPage() {
  const [filterType, setFilterType] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const qc = useQueryClient();

  const { data, isLoading } = useQuery<PageResult<Notification>>({
    queryKey: ['notifications', filterType, page],
    queryFn: () => {
      const p = new URLSearchParams({ page: String(page), size: '20' });
      if (filterType) p.set('type', filterType);
      return apiClient<PageResult<Notification>>(`/api/user/notifications?${p.toString()}`);
    },
  });

  const { data: unread } = useQuery<{ count: number }>({
    queryKey: ['notifications', 'unread'],
    queryFn: () => apiClient<{ count: number }>('/api/user/notifications/unread-count'),
    refetchInterval: 30_000,
  });

  const markAllRead = useMutation({
    mutationFn: () => apiClient<void>('/api/user/notifications/read-all', { method: 'PUT' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['notifications'] }); },
  });

  const markRead = useMutation({
    mutationFn: (id: number) => apiClient<void>(`/api/user/notifications/${id}/read`, { method: 'PUT' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['notifications'] }); },
  });

  return (
    <div className="mx-auto max-w-2xl px-4 pt-8 pb-20">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-2">
          <h1 className="text-xl font-light text-ink-black">消息中心</h1>
          {(unread?.count ?? 0) > 0 && (
            <span className="px-2 py-0.5 text-xs rounded-full bg-danger text-white">{unread?.count}</span>
          )}
        </div>
        {(unread?.count ?? 0) > 0 && (
          <Button variant="ghost" size="sm" onClick={() => markAllRead.mutate()}>
            <Check className="h-4 w-4 mr-1" />全部已读
          </Button>
        )}
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-4 overflow-x-auto pb-1">
        {TABS.map((t) => (
          <button
            key={t.label}
            onClick={() => { setFilterType(t.value as any); setPage(1); }}
            className={`shrink-0 px-4 py-1.5 text-sm rounded-full border transition-colors ${
              filterType === t.value
                ? 'border-accent bg-accent/5 text-accent'
                : 'border-warm-gray-200 text-text-tertiary hover:border-warm-gray-400'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-16 rounded-xl bg-warm-gray-100 animate-pulse" />
          ))}
        </div>
      )}

      {/* Empty */}
      {(data?.records?.length ?? 0) === 0 && (
        <div className="text-center py-16">
          <Bell className="mx-auto h-10 w-10 text-warm-gray-400" />
          <p className="mt-3 text-sm text-text-secondary">暂无消息</p>
        </div>
      )}

      {/* Notification list */}
      <div className="space-y-2">
        {data?.records?.map((n) => {
          const cfg = TYPE_CONFIG[n.type] || TYPE_CONFIG.SYSTEM;
          const Icon = cfg.icon;
          return (
            <Link key={n.id} href={n.linkUrl || '#'} onClick={() => { if (n.isRead === 0) markRead.mutate(n.id); }}>
              <GlassCard className={`flex items-start gap-3 p-3 transition-opacity ${n.isRead === 1 ? 'opacity-60' : ''}`} blur="sm">
                <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${n.isRead === 0 ? 'bg-accent/10' : 'bg-warm-gray-100'}`}>
                  <Icon className={`h-4 w-4 ${cfg.color}`} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <p className={`text-sm ${n.isRead === 0 ? 'font-medium text-ink-black' : 'text-text-secondary'}`}>
                      {n.title}
                    </p>
                    {n.isRead === 0 && <span className="h-2 w-2 rounded-full bg-accent shrink-0" />}
                  </div>
                  {n.content && (
                    <p className="text-xs text-text-tertiary mt-0.5 line-clamp-1">{n.content}</p>
                  )}
                  <p className="text-xs text-text-tertiary mt-1">
                    {n.senderName && <span>{n.senderName} · </span>}
                    {n.createTime ? timeAgo(n.createTime) : ''}
                  </p>
                </div>
              </GlassCard>
            </Link>
          );
        })}
      </div>

      {/* Pagination */}
      {data && data.pages > 1 && (
        <div className="flex justify-center gap-2 mt-6">
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-gray-200 disabled:opacity-30">上一页</button>
          <span className="px-3 py-1.5 text-sm text-text-secondary">{page}/{data.pages}</span>
          <button onClick={() => setPage((p) => Math.min(data.pages, p + 1))} disabled={page >= data.pages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-gray-200 disabled:opacity-30">下一页</button>
        </div>
      )}
    </div>
  );
}
