'use client';

import { useState } from 'react';
import { useAuthStore } from '@icedmall/auth';
import { useBalance, useRecharge, apiClient, type UserInfoResp } from '@icedmall/api';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import Link from 'next/link';

export default function ProfilePage() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);

  // 余额域归 wallet-service —— `GET /api/wallet` / `POST /api/wallet/recharge?amount=`
  // ★ 原 `/api/user/balance[/recharge]` 已下线；`UserInfoResp` 自 V5.4 起也不含 balance
  const { data: balance } = useBalance();
  const recharge = useRecharge();

  const [rechargeYuan, setRechargeYuan] = useState('');
  const [rechargeMsg, setRechargeMsg] = useState('');
  const [uploading, setUploading] = useState(false);

  const handleRecharge = () => {
    const val = Number(rechargeYuan);
    if (isNaN(val) || val <= 0) { setRechargeMsg('请输入有效金额'); return; }
    setRechargeMsg('');
    // 金额一律以「分」的整数提交（展示层才 /100），不使用浮点参与计算
    const cents = Math.round(val * 100);
    recharge.mutate(cents, {
      onSuccess: () => { setRechargeYuan(''); setRechargeMsg('充值成功'); },
      onError: (e: any) => setRechargeMsg(e?.message ?? '充值失败'),
    });
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
      // 注意：不能用 apiClient —— 它强制 `Content-Type: application/json`，
      // 会破坏 multipart 边界；这里保留裸 fetch 以让浏览器自行设置 boundary。
      // 路径 `/api/user/profile/avatar` 与后端一致（POST multipart, ≤5MB, jpg/png/webp）。
      const base = process.env.NEXT_PUBLIC_API_URL || '';
      const token = document.cookie.split('; ').find(r => r.startsWith('access_token='))?.split('=')[1];
      const res = await fetch(`${base}/api/user/profile/avatar`, {
        method: 'POST',
        credentials: 'include',
        headers: token ? { Authorization: `Bearer ${decodeURIComponent(token)}` } : {},
        body: formData,
      });
      if (!res.ok) throw new Error(`上传失败: ${res.status}`);
      const data = await res.json();
      if (data.code === 200) {
        // 刷新用户信息（含新头像 URL）—— 报文 snake_case → 视图模型 camelCase
        const info = await apiClient<UserInfoResp>('/api/user/info');
        setUser({
          userId: String(info.user_id ?? ''),
          username: info.username ?? '',
          phone: info.phone,
          avatar: info.avatar,
        });
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
          <PriceDisplay cents={balance ?? 0} size="md" />
        </div>
        <div className="flex gap-2">
          <input type="number" value={rechargeYuan} onChange={(e) => setRechargeYuan(e.target.value)}
            placeholder="充值金额（元）" min="0.01" step="0.01"
            className="flex-1 rounded-xl border border-warm-gray-200 bg-white px-4 py-2 text-sm outline-none focus:border-accent" />
          <Button variant="secondary" size="md" loading={recharge.isPending} onClick={handleRecharge}>充值</Button>
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
