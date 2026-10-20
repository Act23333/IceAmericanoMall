import { View, Text, Image } from '@tarojs/components';
import Taro, { useDidShow } from '@tarojs/taro';
import { useState } from 'react';
import { apiClient, getAccessToken, clearTokens, getSavedUser, saveUser } from '../../utils/api';
import './index.scss';

const MENUS = [
  { icon: '📦', title: '我的订单', url: '/pages/orders/index' },
  { icon: '📍', title: '收货地址', url: '/pages/addresses/index' },
  { icon: '⭐', title: '我的收藏', url: '/pages/favorites/index' },
  { icon: '🎫', title: '优惠券', url: '/pages/coupons/index' },
  { icon: '💎', title: '积分中心', url: '/pages/points/index' },
  { icon: '✅', title: '每日签到', url: '/pages/sign/index' },
  { icon: '🔔', title: '消息中心', url: '/pages/notifications/index' },
];

export default function ProfilePage() {
  const [user, setUser] = useState<any>(null);
  const [wxProfile, setWxProfile] = useState<{ nickName?: string; avatarUrl?: string } | null>(null);
  const [unread, setUnread] = useState(0);

  useDidShow(() => {
    const token = getAccessToken();
    if (token) fetchUser();
    // 读取本地缓存的微信用户信息
    const saved = getSavedUser();
    if (saved?.nickName || saved?.avatarUrl) {
      setWxProfile(saved);
    }
    fetchUnread();
  });

  async function fetchUser() {
    try {
      const data = await apiClient<any>('/api/user/info');
      setUser(data);
      saveUser({ ...getSavedUser(), ...data });
    } catch { setUser(null); }
  }

  async function fetchUnread() {
    try {
      // ★ 路径更正：`/api/notifications/unread-count`（去掉 `/user` 前缀）
      // ★ 字段更正：响应为 `{"unread": n}`（Map<String,Integer>），旧代码读的 `.count` 不存在
      const data = await apiClient<{ unread: number }>('/api/notifications/unread-count');
      setUnread(data?.unread || 0);
    } catch {}
  }

  function handleLogout() {
    clearTokens();
    setUser(null);
    setWxProfile(null);
    Taro.showToast({ title: '已退出', icon: 'none' });
  }

  function goLogin() {
    Taro.navigateTo({ url: '/pages/login/index' });
  }

  return (
    <View className="page">
      {/* Header — 头像 + 昵称 */}
      <View className="profile-header" onClick={() => user ? null : goLogin()}>
        {wxProfile?.avatarUrl ? (
          <Image src={wxProfile.avatarUrl} mode="aspectFill" style="width:100rpx;height:100rpx;border-radius:50rpx" />
        ) : user?.avatar ? (
          <Image src={user.avatar} mode="aspectFill" style="width:100rpx;height:100rpx;border-radius:50rpx" />
        ) : (
          <View className="avatar"><Text>👤</Text></View>
        )}
        <View className="header-text">
          <Text className="nickname">
            {wxProfile?.nickName || user?.username || '登录/注册'}
          </Text>
          <Text className="subtitle">
            {/* ⚠️ UserInfoResp 自 V5.4 起不含 balance（余额归 wallet-service，取 GET /api/wallet）；
                小程序端尚未接余额端点，故此处只显示登录态（见交接摘要「发现但未改」）。 */}
            {user ? '已登录' : '点击登录账号'}
          </Text>
        </View>
        {user && <Text className="arrow">›</Text>}
      </View>

      {/* 快捷入口 */}
      {user && (
        <View className="stats-row">
          <View className="stat-item" onClick={() => Taro.navigateTo({ url: '/pages/coupons/index' })}>
            <Text className="stat-val">—</Text>
            <Text className="stat-label">优惠券</Text>
          </View>
          <View className="stat-item" onClick={() => Taro.navigateTo({ url: '/pages/points/index' })}>
            <Text className="stat-val">—</Text>
            <Text className="stat-label">积分</Text>
          </View>
          <View className="stat-item" onClick={() => Taro.navigateTo({ url: '/pages/notifications/index' })}>
            <Text className="stat-val">{unread}</Text>
            <Text className="stat-label">未读消息</Text>
          </View>
        </View>
      )}

      {/* 菜单 */}
      <View className="menu-section">
        {MENUS.map((item) => (
          <View
            key={item.title}
            className="menu-item"
            onClick={() => {
              if (!user && item.url !== '/pages/coupons/index') {
                goLogin();
                return;
              }
              Taro.navigateTo({ url: item.url });
            }}
          >
            <Text className="menu-icon">{item.icon}</Text>
            <Text className="menu-title">{item.title}</Text>
            {item.title === '消息中心' && unread > 0 && (
              <Text className="badge">{unread}</Text>
            )}
            <Text className="menu-arrow">›</Text>
          </View>
        ))}
      </View>

      {/* 退出 */}
      {user && (
        <View className="logout-btn" onClick={handleLogout}>
          <Text>退出登录</Text>
        </View>
      )}
    </View>
  );
}
