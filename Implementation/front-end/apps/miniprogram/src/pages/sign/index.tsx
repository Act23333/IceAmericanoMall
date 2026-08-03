import { View, Text } from '@tarojs/components';
import Taro from '@tarojs/taro';
import { useState } from 'react';
import { useDidShow } from '@tarojs/taro';
import { apiClient } from '../../utils/api';

export default function SignPage() {
  const [status, setStatus] = useState<any>(null);

  useDidShow(() => fetchStatus());

  async function fetchStatus() {
    try { const data = await apiClient<any>('/api/user/sign/status'); setStatus(data); } catch {}
  }

  async function doSign() {
    try {
      const data = await apiClient<any>('/api/user/sign', { method: 'POST' });
      setStatus(data);
      Taro.showToast({ title: `签到成功 +${data.earnedPoints}积分`, icon: 'success' });
    } catch {}
  }

  return (
    <View className="page">
      <Text className="page-title">每日签到</Text>
      <View className="sign-card" onClick={doSign}>
        <Text className="sign-icon">{status?.signed ? '✅' : '📅'}</Text>
        <Text className="sign-status">{status?.signed ? '今日已签到' : '点击签到'}</Text>
        {status?.signed && <Text className="sign-info">连续 {status.continuousDays} 天 · +{status.earnedPoints}积分</Text>}
      </View>
    </View>
  );
}
