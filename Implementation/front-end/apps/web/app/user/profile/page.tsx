'use client';

import { useState } from 'react';
import { useUserInfo, useRecharge, useBalance } from '@icedmall/api';
import { useAuthStore } from '@icedmall/auth';
import { Button, GlassCard } from '@icedmall/ui';
import { formatPrice, yuanToCents } from '@icedmall/utils';
import Link from 'next/link';

export default function ProfilePage() {
  const authUser = useAuthStore((s) => s.user);
  const { data: apiUser } = useUserInfo();
  const { data: balance } = useBalance();
  const recharge = useRecharge();
  const user = apiUser ?? authUser;

  const [rechargeYuan, setRechargeYuan] = useState('');
  const [rechargeMsg, setRechargeMsg] = useState('');

  const handleRecharge = () => {
    const cents = yuanToCents(Number(rechargeYuan));
    if (cents <= 0) { setRechargeMsg('请输入有效金额'); return; }
    recharge.mutate(cents, {
      onSuccess: (newBal) => { setRechargeYuan(''); setRechargeMsg(`充值成功，余额 ¥${formatPrice(newBal)}`); },
      onError: () => setRechargeMsg('充值失败'),
    });
  };

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-8">个人中心</h1>

      <GlassCard className="p-6 mb-6 flex items-center gap-4">
        <div className="h-16 w-16 rounded-full bg-gradient-to-br from-accent-green/20 to-accent-blue-purple/10 flex items-center justify-center text-2xl">
          {user?.avatar ? <img src={user.avatar} alt="" className="h-full w-full rounded-full object-cover" /> : '👤'}
        </div>
        <div>
          <h2 className="text-lg font-medium text-ink-black" suppressHydrationWarning>{user?.username ?? '用户'}</h2>
          <p className="text-sm text-warm-600 mt-1">{user?.phone}</p>
        </div>
      </GlassCard>

      {/* 余额 + 充值 */}
      <GlassCard className="p-6 mb-6">
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm text-warm-600">账户余额</span>
          <span className="text-lg font-semibold text-ink-black">
            ¥{balance !== undefined ? formatPrice(balance) : user?.balance !== undefined ? formatPrice(user.balance) : '---'}
          </span>
        </div>
        <div className="flex gap-2">
          <input type="number" value={rechargeYuan} onChange={(e) => setRechargeYuan(e.target.value)}
            placeholder="充值金额（元）" min="0.01" step="0.01"
            className="flex-1 rounded-xl border border-warm-200 bg-white px-4 py-2 text-sm outline-none focus:border-accent-green" />
          <Button variant="secondary" size="md" loading={recharge.isPending} onClick={handleRecharge}>充值</Button>
        </div>
        {rechargeMsg && <p className="mt-2 text-xs text-accent-green">{rechargeMsg}</p>}
      </GlassCard>

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
