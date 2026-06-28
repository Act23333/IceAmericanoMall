'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, GlassCard } from '@icedmall/ui';
import { login } from '@icedmall/auth';
import { useAuthStore } from '@icedmall/auth';
import { phoneSchema } from '@icedmall/utils';
import Link from 'next/link';

type LoginMode = 'sms' | 'password';

export default function LoginPage() {
  const router = useRouter();
  const setUser = useAuthStore((s) => s.setUser);

  const [mode, setMode] = useState<LoginMode>('sms');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 校验手机号
    const phoneResult = phoneSchema.safeParse(phone);
    if (!phoneResult.success) {
      setError('请输入正确的手机号');
      return;
    }

    if (mode === 'password' && !password) {
      setError('请输入密码');
      return;
    }
    if (mode === 'sms' && !smsCode) {
      setError('请输入验证码');
      return;
    }

    setLoading(true);
    try {
      const result = await login({
        identity: phone,
        credential: mode === 'password' ? password : smsCode,
        identityType: 'PHONE',
        credentialType: mode === 'password' ? 'PASSWORD' : 'SMS_CODE',
      });

      setUser({
        userId: String(result.user_id ?? ''),
        username: result.username ?? '',
        nickname: result.username ?? '',
        role: 'BUYER',
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

        {/* 模式切换 */}
        <div className="mt-8 flex rounded-xl bg-warm-100 p-1">
          <button
            onClick={() => setMode('sms')}
            className={`flex-1 py-2 text-sm rounded-lg transition-all ${
              mode === 'sms'
                ? 'bg-white text-ink-black shadow-sm'
                : 'text-warm-600 hover:text-ink-soft'
            }`}
          >
            验证码登录
          </button>
          <button
            onClick={() => setMode('password')}
            className={`flex-1 py-2 text-sm rounded-lg transition-all ${
              mode === 'password'
                ? 'bg-white text-ink-black shadow-sm'
                : 'text-warm-600 hover:text-ink-soft'
            }`}
          >
            密码登录
          </button>
        </div>

        {/* 表单 */}
        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          {/* 手机号 */}
          <div>
            <label className="text-sm text-ink-soft mb-1.5 block">手机号</label>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="请输入手机号"
              maxLength={11}
              className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
            />
          </div>

          {/* 密码 */}
          {mode === 'password' && (
            <div>
              <label className="text-sm text-ink-soft mb-1.5 block">密码</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码"
                className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
              />
            </div>
          )}

          {/* 验证码 */}
          {mode === 'sms' && (
            <div>
              <label className="text-sm text-ink-soft mb-1.5 block">验证码</label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={smsCode}
                  onChange={(e) => setSmsCode(e.target.value)}
                  placeholder="6位验证码"
                  maxLength={6}
                  className="flex-1 rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
                />
                <button
                  type="button"
                  className="shrink-0 rounded-xl bg-warm-100 px-4 py-2.5 text-sm text-ink-soft hover:bg-warm-200 transition-colors"
                >
                  获取验证码
                </button>
              </div>
            </div>
          )}

          {/* 错误提示 */}
          {error && (
            <p className="text-sm text-danger text-center">{error}</p>
          )}

          {/* 提交 */}
          <Button
            type="submit"
            variant="primary"
            size="lg"
            loading={loading}
            className="w-full mt-2"
          >
            登录
          </Button>
        </form>

        {/* 底部链接 */}
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
