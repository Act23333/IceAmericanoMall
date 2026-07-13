'use client';

import { useUserInfo } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import Link from 'next/link';

export default function ProfilePage() {
  const { data: user } = useUserInfo();

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-8">个人中心</h1>

      {/* 用户信息卡片 */}
      <GlassCard className="p-6 mb-6 flex items-center gap-4">
        <div className="h-16 w-16 rounded-full bg-gradient-to-br from-accent-green/20 to-accent-blue-purple/10 flex items-center justify-center text-2xl">
          {user?.avatar ? <img src={user.avatar} alt="" className="h-full w-full rounded-full object-cover" /> : '👤'}
        </div>
        <div>
          <h2 className="text-lg font-medium text-ink-black">{user?.username ?? '加载中...'}</h2>
          <p className="text-sm text-warm-600 mt-1">{user?.phone}</p>
        </div>
      </GlassCard>

      {/* 资产 */}
      <GlassCard className="p-6 mb-6">
        <div className="flex items-center justify-between">
          <span className="text-sm text-warm-600">账户余额</span>
          <span className="text-lg font-semibold text-ink-black">¥{user ? formatPrice(user.balance) : '---'}</span>
        </div>
      </GlassCard>

      {/* 菜单 */}
      <div className="space-y-2">
        {[
          { icon: '📦', label: '我的订单', href: '/shop/orders' },
          { icon: '📍', label: '收货地址', href: '/user/addresses' },
          { icon: '⭐', label: '我的收藏', href: '/user/favorites' },
          { icon: '🕐', label: '浏览历史', href: '/user/history' },
          { icon: '🎁', label: '每日签到', href: '/user/sign-in' },
        ].map((item) => (
          <Link key={item.href} href={item.href}>
            <GlassCard className="flex items-center gap-4 p-4 hover:shadow-glass-lg transition-shadow cursor-pointer" blur="sm">
              <span className="text-xl">{item.icon}</span>
              <span className="flex-1 text-sm text-ink-black">{item.label}</span>
              <span className="text-warm-400">›</span>
            </GlassCard>
          </Link>
        ))}
      </div>
    </div>
  );
}
