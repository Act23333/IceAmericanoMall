import { View, Text } from '@tarojs/components';
import { useState } from 'react';
import { useDidShow } from '@tarojs/taro';
import { apiClient } from '../../utils/api';

export default function PointsPage() {
  const [balance, setBalance] = useState<number>(0);
  const [logs, setLogs] = useState<any[]>([]);

  useDidShow(() => {
    fetchBalance();
    fetchLogs();
  });

  async function fetchBalance() {
    // ★ 路径更正：积分域归 marketing-service —— `GET /api/points/balance`
    //   （原 `/api/user/points/balance` 在后端不存在）
    try { const data = await apiClient<{user_id: number; balance: number}>('/api/points/balance'); setBalance(data?.balance || 0); } catch {}
  }
  async function fetchLogs() {
    // ★ 路径更正：`GET /api/points/history`
    try { const data = await apiClient<any>('/api/points/history?page=1&size=30'); setLogs(data?.records || []); } catch {}
  }

  return (
    <View className="page">
      <Text className="page-title">积分中心</Text>
      <View className="balance-card">
        <Text className="balance-val">{balance}</Text>
        <Text className="balance-label">当前积分</Text>
      </View>
      <View className="section-title">积分明细</View>
      {logs.map((log: any, i: number) => (
        <View key={i} className="log-item">
          <Text className="log-source">{log.source || '积分变动'}</Text>
          <Text className={`log-amount ${log.type === 1 ? 'positive' : ''}`}>{log.type === 1 ? '+' : '-'}{log.points}</Text>
        </View>
      ))}
    </View>
  );
}
