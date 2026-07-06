'use client';

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Button, GlassCard } from '@icedmall/ui';
import { login } from '@icedmall/auth';
import { useAuthStore } from '@icedmall/auth';
import { phoneSchema, passwordSchema } from '@icedmall/utils';
import Link from 'next/link';

/**
 * 登录页 — 支持后端全部 3 种登录方式:
 *   1. 手机号 + 验证码   (PHONE + SMS_CODE)
 *   2. 手机号 + 密码     (PHONE + PASSWORD)
 *   3. 用户名 + 密码     (USERNAME + PASSWORD)
 */
type LoginTab = 'sms' | 'password';

export default function LoginPage() {
  const router = useRouter();
  const setUser = useAuthStore((s) => s.setUser);

  const [tab, setTab] = useState<LoginTab>('sms');
  const [phone, setPhone] = useState('');
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [smsCountdown, setSmsCountdown] = useState(0);

  const sendSms = async () => {
    if (smsCountdown > 0) return;
    try {
      await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/user/code`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone }),
      });
      setSmsCountdown(60);
      const timer = setInterval(() => setSmsCountdown((c) => { if (c <= 1) { clearInterval(timer); return 0; } return c - 1; }), 1000);
    } catch { /* ignore */ }
  };

  /** 判断账号类型: 纯数字11位→PHONE, 否则→USERNAME */
  const detectIdentityType = useCallback((val: string): 'PHONE' | 'USERNAME' => {
    return /^1[3-9]\d{9}$/.test(val.trim()) ? 'PHONE' : 'USERNAME';
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (tab === 'sms') {
      if (!phoneSchema.safeParse(phone).success) { setError('请输入正确的手机号'); return; }
      if (smsCode.length !== 6) { setError('请输入6位验证码'); return; }
    } else {
      // 密码登录: 账号可以是手机号或用户名
      if (!account.trim()) { setError('请输入手机号或用户名'); return; }
      if (!passwordSchema.safeParse(password).success) { setError('密码需8-32位，包含字母和数字'); return; }
    }

    setLoading(true);
    try {
      const identityType = tab === 'sms' ? 'PHONE' : detectIdentityType(account);
      const result = await login({
        account: tab === 'sms' ? phone : account.trim(),
        credential: tab === 'sms' ? smsCode : password,
        identityType,
        credentialType: tab === 'sms' ? 'SMS_CODE' : 'PASSWORD',
      });

      setUser({
        userId: String(result.user_id ?? ''),
        username: result.username ?? '',
      });
      router.push('/marketplace');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '登录失败，请重试';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-mist-white px-4">
      <GlassCard className="w-full max-w-md p-8 md:p-10">
        {/* Logo */}
        <div className="text-center">
          <Link href="/" className="text-accent-green text-2xl">◆</Link>
          <h1 className="mt-3 text-xl font-medium text-ink-black">登录冰美商城</h1>
          <p className="mt-1 text-sm text-warm-600">欢迎回来</p>
        </div>

        {/* 标签切换 */}
        <div className="mt-8 flex rounded-xl bg-warm-100 p-1" role="tablist">
          <div
            role="tab" aria-selected={tab === 'sms'}
            onClick={() => setTab('sms')}
            className={`flex-1 py-2 text-sm text-center rounded-lg cursor-pointer transition-all select-none ${
              tab === 'sms' ? 'bg-white text-ink-black shadow-sm' : 'text-warm-600 hover:text-ink-soft'
            }`}
          >
            验证码登录
          </div>
          <div
            role="tab" aria-selected={tab === 'password'}
            onClick={() => setTab('password')}
            className={`flex-1 py-2 text-sm text-center rounded-lg cursor-pointer transition-all select-none ${
              tab === 'password' ? 'bg-white text-ink-black shadow-sm' : 'text-warm-600 hover:text-ink-soft'
            }`}
          >
            密码登录
          </div>
        </div>

        {/* 表单 */}
        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          {/* ── SMS 模式: 手机号 ── */}
          {tab === 'sms' && (
            <div>
              <label className="text-sm text-ink-soft mb-1.5 block">手机号</label>
              <input
                type="tel" value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="请输入手机号" maxLength={11}
                className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
              />
            </div>
          )}

          {/* ── 密码模式: 手机号或用户名 ── */}
          {tab === 'password' && (
            <div>
              <label className="text-sm text-ink-soft mb-1.5 block">手机号或用户名</label>
              <input
                type="text" value={account}
                onChange={(e) => setAccount(e.target.value)}
                placeholder="手机号或用户名"
                className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
              />
            </div>
          )}

          {/* ── 验证码 (SMS 模式) ── */}
          {tab === 'sms' && (
            <div>
              <label className="text-sm text-ink-soft mb-1.5 block">验证码</label>
              <div className="flex gap-2">
                <input
                  type="text" value={smsCode}
                  onChange={(e) => setSmsCode(e.target.value)}
                  placeholder="6位验证码" maxLength={6}
                  className="flex-1 rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
                />
                <button type="button" onClick={sendSms} disabled={smsCountdown > 0}
                  className="shrink-0 rounded-xl bg-warm-100 px-4 py-2.5 text-sm text-ink-soft hover:bg-warm-200 transition-colors disabled:opacity-50"
                >
                  {smsCountdown > 0 ? `${smsCountdown}s` : '获取验证码'}
                </button>
              </div>
            </div>
          )}

          {/* ── 密码 (密码模式) ── */}
          {tab === 'password' && (
            <div>
              <label className="text-sm text-ink-soft mb-1.5 block">密码</label>
              <input
                type="password" value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="8-32位，包含字母和数字"
                className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
              />
            </div>
          )}

          {error && <p className="text-sm text-danger text-center">{error}</p>}

          <Button type="submit" variant="primary" size="lg" loading={loading} className="w-full mt-2">
            {tab === 'sms' ? '登录' : '登录'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-warm-600">
          还没有账号？
          <Link href="/auth/register" className="ml-1 text-accent-green hover:text-accent-green-light font-medium">
            立即注册
          </Link>
        </p>
      </GlassCard>
    </div>
  );
}
