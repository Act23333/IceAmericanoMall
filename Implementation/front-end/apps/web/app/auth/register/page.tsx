'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, GlassCard } from '@icedmall/ui';
import { register } from '@icedmall/auth';
import { useAuthStore } from '@icedmall/auth';
import { phoneSchema, passwordSchema, usernameSchema, smsCodeSchema } from '@icedmall/utils';
import { getUserMessage } from '@icedmall/api';
import Link from 'next/link';

/**
 * 注册页 — 对齐后端 RegisterReq:
 *   { phone, password, code(SMS), username? }
 */
export default function RegisterPage() {
  const router = useRouter();
  const setUser = useAuthStore((s) => s.setUser);

  const [phone, setPhone] = useState('');
  const [username, setUsername] = useState('');
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
        body: JSON.stringify({
          phone,
          requestId: crypto.randomUUID?.() ?? `${Date.now()}`,
          captchaTicket: 'mock_ticket',
          lotNumber: 'mock_lot',
          captchaOutput: 'mock_output',
          passToken: 'mock_token',
          genTime: String(Math.floor(Date.now() / 1000)),
        }),
      });
      setSmsCountdown(60);
      const timer = setInterval(() => setSmsCountdown((c) => { if (c <= 1) { clearInterval(timer); return 0; } return c - 1; }), 1000);
    } catch { /* ignore */ }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!phoneSchema.safeParse(phone).success) { setError('请输入正确的手机号'); return; }
    if (!passwordSchema.safeParse(password).success) { setError('密码需8-32位，包含字母和数字'); return; }
    if (!smsCodeSchema.safeParse(smsCode).success) { setError('请输入6位数字验证码'); return; }
    // username 可选，但如果填了需验证
    if (username.trim() && !usernameSchema.safeParse(username.trim()).success) {
      setError('用户名需3-20位，以字母开头'); return;
    }

    setLoading(true);
    try {
      const result = await register({
        phone,
        password,
        code: smsCode,
        username: username.trim() || undefined,
      });

      setUser({
        userId: String(result.user_id ?? ''),
        username: result.username ?? phone,
      });
      router.push('/marketplace');
    } catch (err: unknown) {
      setError(getUserMessage(err as { code?: number; message?: string }));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-mist-white px-4">
      <GlassCard className="w-full max-w-md p-8 md:p-10">
        <div className="text-center">
          <Link href="/" className="text-accent-green text-2xl">◆</Link>
          <h1 className="mt-3 text-xl font-medium text-ink-black">创建账号</h1>
          <p className="mt-1 text-sm text-warm-600">注册冰美商城，开启品质生活</p>
        </div>

        <form onSubmit={handleSubmit} className="mt-8 space-y-4">
          {/* 手机号 */}
          <div>
            <label className="text-sm text-ink-soft mb-1.5 block">手机号</label>
            <input type="tel" value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="请输入手机号" maxLength={11}
              className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
            />
          </div>

          {/* 用户名 (可选) — 对齐后端 RegisterReq.username */}
          <div>
            <label className="text-sm text-ink-soft mb-1.5 block">
              用户名 <span className="text-warm-400 font-normal">(选填)</span>
            </label>
            <input type="text" value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="3-20位，以字母开头"
              className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
            />
          </div>

          {/* 验证码 */}
          <div>
            <label className="text-sm text-ink-soft mb-1.5 block">短信验证码</label>
            <div className="flex gap-2">
              <input type="text" value={smsCode}
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

          {/* 密码 */}
          <div>
            <label className="text-sm text-ink-soft mb-1.5 block">设置密码</label>
            <input type="password" value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="8-32位，包含字母和数字"
              className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
            />
            <p className="mt-1 text-xs text-warm-400">至少8位，需包含字母和数字</p>
          </div>

          {error && <p className="text-sm text-danger text-center">{error}</p>}

          <Button type="submit" variant="primary" size="lg" loading={loading} className="w-full mt-2">
            注册
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-warm-600">
          已有账号？
          <Link href="/auth/login" className="ml-1 text-accent-green hover:text-accent-green-light font-medium">
            去登录
          </Link>
        </p>
      </GlassCard>
    </div>
  );
}
