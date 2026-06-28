import { View, Text } from '@tarojs/components';
import './index.scss';

export default function ProfilePage() {
  return (
    <View className="page">
      {/* 头部 */}
      <View className="profile-header">
        <View className="avatar" />
        <Text className="nickname">未登录</Text>
        <Text className="login-hint">登录/注册 →</Text>
      </View>

      {/* 菜单 */}
      <View className="menu-section">
        {[
          { icon: '📦', title: '我的订单', desc: '查看全部订单' },
          { icon: '📍', title: '收货地址', desc: '管理收货地址' },
          { icon: '⭐', title: '我的收藏', desc: '收藏的好物' },
          { icon: '⚙️', title: '设置', desc: '账号与偏好' },
        ].map((item) => (
          <View key={item.title} className="menu-item">
            <Text className="menu-icon">{item.icon}</Text>
            <View className="menu-text">
              <Text className="menu-title">{item.title}</Text>
              <Text className="menu-desc">{item.desc}</Text>
            </View>
            <Text className="menu-arrow">›</Text>
          </View>
        ))}
      </View>
    </View>
  );
}
