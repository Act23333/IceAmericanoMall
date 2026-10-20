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
      // ★ 路径更正：`/api/notifications/**`（去掉 `/user` 前缀）
      // ★ 分页参数名更正：`page_num` / `page_size`（契约 §4.2.9 口径，后端已统一为
      //   @RequestParam(name = "page_num"/"page_size")——线上查询参数一律 snake_case）
      const q = tab ? `?type=${tab}&page_num=1&page_size=50` : '?page_num=1&page_size=50';
      const data = await apiClient<any>(`/api/notifications${q}`);
      setList(data?.records || []);
    } catch {}
  }

  async function markRead(id: number) {
    try { await apiClient(`/api/notifications/${id}/read`, { method: 'PUT' }); fetchList(); } catch {}
  }

  async function markAll() {
    try { await apiClient('/api/notifications/read-all', { method: 'PUT' }); fetchList(); } catch {}
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
          // ★ 字段更正：InAppMessageVO 为 snake_case（sender_name / is_read）
          <View key={n.id} className={`notif-item ${n.is_read === 0 ? 'unread' : ''}`} onClick={() => markRead(n.id)}>
            <Text className="notif-title">{n.title}{n.sender_name ? ` — ${n.sender_name}` : ''}</Text>
            {n.content && <Text className="notif-content">{n.content}</Text>}
            {n.is_read === 0 && <View className="unread-dot" />}
          </View>
        ))}
    </View>
  );
}
