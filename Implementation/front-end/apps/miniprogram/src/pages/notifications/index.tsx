import { View, Text } from '@tarojs/components';
import { useState } from 'react';
import { useDidShow } from '@tarojs/taro';
import { apiClient } from '../../utils/api';

export default function NotificationsPage() {
  const [list, setList] = useState<any[]>([]);
  const [tab, setTab] = useState<string | null>(null);

  useDidShow(() => fetchList());

  async function fetchList() {
    try {
      const q = tab ? `?type=${tab}&page=1&size=50` : '?page=1&size=50';
      const data = await apiClient<any>(`/api/user/notifications${q}`);
      setList(data?.records || []);
    } catch {}
  }

  async function markRead(id: number) {
    try { await apiClient(`/api/user/notifications/${id}/read`, { method: 'PUT' }); fetchList(); } catch {}
  }

  async function markAll() {
    try { await apiClient('/api/user/notifications/read-all', { method: 'PUT' }); fetchList(); } catch {}
  }

  const TABS = [{ l: '全部', v: null }, { l: '点赞', v: 'LIKE' }, { l: '回复', v: 'REPLY' }, { l: '关注', v: 'FOLLOW' }, { l: '订单', v: 'ORDER' }];

  return (
    <View className="page">
      <View className="page-header">
        <Text className="page-title">消息中心</Text>
        <Text className="mark-all" onClick={markAll}>全部已读</Text>
      </View>
      <View className="tab-row">{TABS.map(t => <Text key={t.l} className={`tab ${tab === t.v ? 'active' : ''}`} onClick={() => { setTab(t.v as any); setTimeout(fetchList, 0); }}>{t.l}</Text>)}</View>
      {list.length === 0 ? <View className="empty"><Text>暂无消息</Text></View> :
        list.map((n: any) => (
          <View key={n.id} className={`notif-item ${n.isRead === 0 ? 'unread' : ''}`} onClick={() => markRead(n.id)}>
            <Text className="notif-title">{n.title}{n.senderName ? ` — ${n.senderName}` : ''}</Text>
            {n.content && <Text className="notif-content">{n.content}</Text>}
            {n.isRead === 0 && <View className="unread-dot" />}
          </View>
        ))}
    </View>
  );
}
