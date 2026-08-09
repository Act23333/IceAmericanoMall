'use client';

import { useState } from 'react';
import { useAuthStore } from '@icedmall/auth';
import { Button, GlassCard } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import Link from 'next/link';

function yuanToCents(yuan: number): number {
  return Math.round(yuan * 100);
}

export default function ProfilePage() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);

  const [rechargeYuan, setRechargeYuan] = useState('');
  const [rechargeMsg, setRechargeMsg] = useState('');
  const [recharging, setRecharging] = useState(false);
  const [uploading, setUploading] = useState(false);

  const handleRecharge = async () => {
    const val = Number(rechargeYuan);
    if (isNaN(val) || val <= 0) { setRechargeMsg('请输入有效金额'); return; }
    setRecharging(true);
    setRechargeMsg('');
    try {
      const base = process.env.NEXT_PUBLIC_API_URL || '';
      const res = await fetch(`${base}/api/user/balance/recharge?amount=${yuanToCents(val)}`, {
        method: 'POST', credentials: 'include',
      });
      const data = await res.json();
      if (data.code === 200) {
        setRechargeYuan('');
        setRechargeMsg(`充值成功，余额 ¥${formatPrice(data.data)}`);
        const infoRes = await fetch(`${base}/api/user/info`, { credentials: 'include' });
        const infoData = await infoRes.json();
        if (infoData.code === 200 && infoData.data) setUser(infoData.data);
      } else {
        setRechargeMsg(data.msg || '充值失败');
      }
    } catch {
      setRechargeMsg('网络错误，请重试');
    } finally {
      setRecharging(false);
    }
  };

  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      alert('图片大小不能超过5MB');
      return;
    }
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const base = process.env.NEXT_PUBLIC_API_URL || '';
      const token = document.cookie.split('; ').find(r => r.startsWith('access_token='))?.split('=')[1];
      const res = await fetch(`${base}/api/user/profile/avatar`, {
        method: 'POST',
        credentials: 'include',
        headers: token ? { Authorization: `Bearer ${decodeURIComponent(token)}` } : {},
        body: formData,
      });
      const data = await res.json();
      if (data.code === 200) {
        // 刷新用户信息（含新头像URL）
        const infoRes = await fetch(`${base}/api/user/info`, { credentials: 'include' });
        const infoData = await infoRes.json();
        if (infoData.code === 200 && infoData.data) setUser(infoData.data);
      }
    } catch (err) {
      console.error('头像上传失败', err);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-8">个人中心</h1>

      <GlassCard className="p-6 mb-6 flex items-center gap-4">
        {/* 头像 — 可点击上传 (用 label 触发 input，比 ref+onClick 更可靠) */}
        <label className="relative h-16 w-16 rounded-full bg-gradient-to-br from-accent-green/20 to-accent-blue-purple/10 flex items-center justify-center text-2xl cursor-pointer group overflow-hidden">
          {user?.avatar ? (
            <img src={user.avatar} alt="" className="h-full w-full rounded-full object-cover pointer-events-none" />
          ) : (
            <span className="pointer-events-none">👤</span>
          )}
          {/* hover overlay */}
          <div className="absolute inset-0 bg-black/30 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">
            <span className="text-white text-xs">{uploading ? '...' : '📷'}</span>
          </div>
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="sr-only"
            onChange={handleAvatarUpload}
            disabled={uploading}
          />
        </label>
        <div>
          <h2 className="text-lg font-medium text-ink-black">{user?.username ?? '用户'}</h2>
          {user?.userId && <p className="text-xs text-text-tertiary">ID: {user.userId}</p>}
          <p className="text-sm text-text-secondary mt-1">{user?.phone}</p>
        </div>
      </GlassCard>

      {/* 余额 + 充值 */}
      <GlassCard className="p-6 mb-6">
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm text-text-secondary">账户余额</span>
          <span className="text-lg font-semibold text-ink-black">
            ¥{user?.balance !== undefined ? formatPrice(user.balance) : '---'}
          </span>
        </div>
        <div className="flex gap-2">
          <input type="number" value={rechargeYuan} onChange={(e) => setRechargeYuan(e.target.value)}
            placeholder="充值金额（元）" min="0.01" step="0.01"
            className="flex-1 rounded-xl border border-warm-gray-200 bg-white px-4 py-2 text-sm outline-none focus:border-accent" />
          <Button variant="secondary" size="md" loading={recharging} onClick={handleRecharge}>充值</Button>
        </div>
        {rechargeMsg && <p className="mt-2 text-xs text-accent">{rechargeMsg}</p>}
      </GlassCard>

      <div className="space-y-2">
        {[
          { icon: '📦', label: '我的订单', href: '/shop/orders' },
          { icon: '📍', label: '收货地址', href: '/user/addresses' },
          { icon: '⭐', label: '我的收藏', href: '/user/favorites' },
          { icon: '🕐', label: '浏览历史', href: '/user/history' },
          { icon: '🎟️', label: '优惠券', href: '/user/coupons' },
          { icon: '🎁', label: '每日签到', href: '/user/sign-in' },
        ].map((item) => (
          <Link key={item.href} href={item.href}>
            <GlassCard className="flex items-center gap-4 p-4 hover:shadow-glass-lg transition-shadow cursor-pointer" blur="sm">
              <span className="text-xl">{item.icon}</span>
              <span className="flex-1 text-sm text-ink-black">{item.label}</span>
              <span className="text-warm-gray-400">›</span>
            </GlassCard>
          </Link>
        ))}
      </div>
    </div>
  );
}
