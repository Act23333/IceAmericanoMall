import { View, Text } from '@tarojs/components';
import Taro from '@tarojs/taro';
import { useState } from 'react';
import { useDidShow } from '@tarojs/taro';
import { apiClient } from '../../utils/api';

export default function SignPage() {
  const [status, setStatus] = useState<any>(null);

  useDidShow(() => fetchStatus());

  async function fetchStatus() {
    // ★ 路径更正：签到域归 marketing-service —— `GET /api/sign/status`
    //   （原 `/api/user/sign/status` 在后端不存在）
    try { const data = await apiClient<any>('/api/sign/status'); setStatus(data); } catch {}
  }

  async function doSign() {
    try {
      // ★ 路径更正：`POST /api/sign`
      const data = await apiClient<any>('/api/sign', { method: 'POST' });
      setStatus(data);
      // ★ 字段更正：SignResultVO = { signed, earned_points, month_count, continuous_days }
      Taro.showToast({ title: `签到成功 +${data.earned_points ?? 0}积分`, icon: 'success' });
    } catch {}
  }

  return (
    <View className="page">
      <Text className="page-title">每日签到</Text>
      <View className="sign-card" onClick={doSign}>
        <Text className="sign-icon">{status?.signed ? '✅' : '📅'}</Text>
        <Text className="sign-status">{status?.signed ? '今日已签到' : '点击签到'}</Text>
        {status?.signed && <Text className="sign-info">连续 {status.continuous_days} 天 · 本月 {status.month_count} 次 · +{status.earned_points}积分</Text>}
      </View>
    </View>
  );
}
