import { View, Text } from '@tarojs/components';
import Taro from '@tarojs/taro';
import { useState } from 'react';
import { useDidShow } from '@tarojs/taro';
import { apiClient } from '../../utils/api';

export default function AddressesPage() {
  const [list, setList] = useState<any[]>([]);

  useDidShow(async () => {
    try { const data = await apiClient<any[]>('/api/user/address/list'); setList(data || []); } catch {}
  });

  async function remove(id: number) {
    try { await apiClient(`/api/user/address/delete/${id}`, { method: 'DELETE' }); setList(list.filter((a: any) => a.id !== id)); } catch {}
  }

  return (
    <View className="page">
      <Text className="page-title">收货地址</Text>
      {list.map((a: any) => (
        <View key={a.id} className="addr-card">
          <View className="addr-header">
            <Text className="addr-name">{a.receiver} {a.phone}</Text>
            {a.defaulted && <Text className="addr-tag">默认</Text>}
          </View>
          <Text className="addr-detail">{a.province}{a.city}{a.district}{a.street}{a.detail}</Text>
          <Text className="del-btn" onClick={() => remove(a.id)}>删除</Text>
        </View>
      ))}
    </View>
  );
}
