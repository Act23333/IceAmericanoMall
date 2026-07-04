'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, GlassCard } from '@icedmall/ui';
import { register } from '@icedmall/auth';
import { useAuthStore } from '@icedmall/auth';
import { phoneSchema, passwordSchema } from '@icedmall/utils';
import Link from 'next/link';

export default function RegisterPage() {
  const router = useRouter();
  const setUser = useAuthStore((s) => s.setUser);

  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 校验
    if (!phoneSchema.safeParse(phone).success) {
      setError('请输入正确的手机号');
      return;
    }
    if (!passwordSchema.safeParse(password).success) {
      setError('密码需8-32位，包含字母和数字');
      return;
    }
    if (smsCode.length !== 6) {
      setError('请输入6位验证码');
      return;
    }

    setLoading(true);
    try {
      const result = await register({
        phone,
        password,
        code: smsCode,
      });

      setUser({
        userId: String(result.user_id ?? ''),
        username: result.username ?? phone,
      });

      router.push('/marketplace');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '注册失败，请重试';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-mist-white px-4">
      <GlassCard className="w-full max-w-md p-8 md:p-10">
        {/* Header */}
        <div className="text-center">
          <Link href="/" className="text-accent-green text-2xl">◆</Link>
          <h1 className="mt-3 text-xl font-medium text-ink-black">创建账号</h1>
          <p className="mt-1 text-sm text-warm-600">注册冰美商城，开启品质生活</p>
        </div>

        {/* 表单 */}
        <form onSubmit={handleSubmit} className="mt-8 space-y-4">
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

          {/* 验证码 */}
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

          {/* 密码 */}
          <div>
            <label className="text-sm text-ink-soft mb-1.5 block">设置密码</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="8-32位，包含字母和数字"
              className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
            />
            <p className="mt-1 text-xs text-warm-400">至少8位，需包含字母和数字</p>
          </div>

          {/* 错误 */}
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
            注册
          </Button>
        </form>

        {/* 底部 */}
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
