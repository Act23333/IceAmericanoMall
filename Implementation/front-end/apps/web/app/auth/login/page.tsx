'use client';

import { useState, useCallback } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Button, GlassCard } from '@icedmall/ui';
import { login } from '@icedmall/auth';
import { useAuthStore } from '@icedmall/auth';
import { apiClient, sendVerificationCode, getUserMessage, getLoginFailureMessage, type LoginResp } from '@icedmall/api';
import { phoneSchema, passwordSchema, smsCodeSchema } from '@icedmall/utils';
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
  const searchParams = useSearchParams();
  const setUser = useAuthStore((s) => s.setUser);

  /** 登录成功后回跳地址（由 auth:logout 事件携带） */
  const redirect = searchParams.get('redirect');
  const goAfterLogin = useCallback((path: string) => {
    router.replace(redirect && redirect.startsWith('/') ? redirect : path);
  }, [router, redirect]);

  const [tab, setTab] = useState<LoginTab>('sms');
  const [phone, setPhone] = useState('');
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [failCount, setFailCount] = useState(0);
  const [cooldown, setCooldown] = useState(0); // 连续失败N次后冷却秒数
  const [smsCountdown, setSmsCountdown] = useState(0);
  const [smsSending, setSmsSending] = useState(false);
  const [smsSent, setSmsSent] = useState(false);

  const sendSms = async () => {
    if (smsCountdown > 0 || smsSending) return;
    setSmsSent(false); setSmsSending(true);
    try {
      // 发码统一走 verification-service（ADR-016 收口）——`POST /api/verification/codes`
      // ★ 原 `/api/user/code` 已下线（该路径在后端不存在）
      await sendVerificationCode({
        channel: 'SMS',
        target: phone,
        scene: 'LOGIN',
        request_id: crypto.randomUUID?.() ?? `${Date.now()}`,
      });
      setSmsCountdown(60);
      setSmsSent(true);
      const timer = setInterval(() => setSmsCountdown((c) => { if (c <= 1) { clearInterval(timer); return 0; } return c - 1; }), 1000);
    } catch (err: unknown) {
      setError(getUserMessage(err as { code?: number; message?: string }));
    }
    setSmsSending(false);
  };

  /** 微信网页授权登录（Mock: code 固定为 mock_code_web）—— `POST /api/auth/web/wechat/login` */
  const handleWechatLogin = async () => {
    setError(''); setLoading(true);
    try {
      const result = await apiClient<LoginResp>(
        '/api/auth/web/wechat/login', { method: 'POST', body: JSON.stringify({ code: 'mock_code_web', state: 'web' }) });
      // LoginVO 不返回 username（后端只回 token + user_id），昵称请取 GET /api/user/info
      setUser({ userId: String(result.user_id ?? ''), username: '微信用户' });
      goAfterLogin('/marketplace');
    } catch (err: unknown) {
      // 登录失败一律中性文案（不得区分「账号不存在 / 密码错 / 账号被冻结」，防账号枚举）
      setError(getLoginFailureMessage(err as { code?: number; message?: string }));
    } finally { setLoading(false); }
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
      if (!smsCodeSchema.safeParse(smsCode).success) { setError('请输入6位数字验证码'); return; }
    } else {
      // 密码登录: 账号可以是手机号或用户名
      if (!account.trim()) { setError('请输入手机号或用户名'); return; }
      if (!passwordSchema.safeParse(password).success) { setError('密码需8-32位，包含字母和数字'); return; }
    }

    setLoading(true);
    try {
      // 请求体 WebLoginReq 无 @JsonProperty → 全平台 SNAKE_CASE 下键名为
      // identity_type / credential_type（旧代码的 identityType/credentialType 会被静默忽略）
      const identityType = tab === 'sms' ? 'PHONE' : detectIdentityType(account);
      const result = await login({
        account: tab === 'sms' ? phone : account.trim(),
        credential: tab === 'sms' ? smsCode : password,
        identity_type: identityType,
        credential_type: tab === 'sms' ? 'SMS_CODE' : 'PASSWORD',
      });

      // LoginVO 不返回 username —— 展示名先用输入的账号，真实昵称由 GET /api/user/info 覆盖
      setUser({
        userId: String(result.user_id ?? ''),
        username: tab === 'sms' ? phone : account.trim(),
      });
      goAfterLogin('/marketplace');
    } catch (err: unknown) {
      // ── 登录失败文案：一律中性 ──────────────────────────────────────────
      // 后端把「账号不存在 / 密码错 / 账号状态不可登录 / 被风控冻结」收敛为同一响应
      // （HTTP 401 + code 3007 + 中性 msg），前端**不得**按 code 分支提示
      // 「该账号不存在」「密码错误」「账号被冻结」—— 任一可区分文案都会变成账号枚举信号。
      setError(getLoginFailureMessage(err as { code?: number; message?: string }));
      // 连续失败3次 → 冷却10秒 (UX限流, 非安全边界)
      const newFails = failCount + 1;
      setFailCount(newFails);
      if (newFails >= 3) {
        setCooldown(10);
        setFailCount(0);
        const timer = setInterval(() => setCooldown((c) => { if (c <= 1) { clearInterval(timer); return 0; } return c - 1; }), 1000);
      }
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
                <button type="button" onClick={sendSms} disabled={smsCountdown > 0 || smsSending}
                  className={`shrink-0 rounded-xl px-4 py-2.5 text-sm transition-colors disabled:opacity-50 ${
                    smsSent ? 'bg-accent-green/10 text-accent-green' : 'bg-warm-100 text-ink-soft hover:bg-warm-200'
                  }`}
                >
                  {smsSending ? '发送中...' : smsSent ? '已发送 ✓' : smsCountdown > 0 ? `${smsCountdown}s` : '获取验证码'}
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

          <Button type="submit" variant="primary" size="lg"
            loading={loading} disabled={cooldown > 0} className="w-full mt-2">
            {cooldown > 0 ? `请${cooldown}秒后重试` : '登录'}
          </Button>
        </form>

        {/* 微信登录 */}
        <div className="mt-4">
          <button type="button" onClick={handleWechatLogin} disabled={loading}
            className="w-full rounded-xl border border-green-400 bg-green-50 px-4 py-2.5 text-sm text-green-700 hover:bg-green-100 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
          >
            <span className="text-lg">💚</span> 微信登录（Mock）
          </button>
        </div>

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
