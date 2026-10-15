'use client';

import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { apiClient, type SignResultVO } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';

/**
 * 每日签到。
 *
 * ✅ 路径更正（积分/签到域归 marketing-service）：
 *    `GET /api/sign/status`、`POST /api/sign` —— 原 `/api/user/sign(+/status)` 在后端不存在。
 * ✅ 字段更正：响应 `SignResultVO` = `{ signed, earned_points, month_count, continuous_days }`
 *    （旧代码读的 `continuousDays` / `points` 均不存在）。
 */
export default function SignInPage() {
  const [signed, setSigned] = useState(false);
  const [errMsg, setErrMsg] = useState('');

  const { data: status, refetch } = useQuery<SignResultVO>({
    queryKey: ['sign', 'status'],
    queryFn: () => apiClient<SignResultVO>('/api/sign/status'),
  });

  const signMutation = useMutation({
    mutationFn: () => apiClient<SignResultVO>('/api/sign', { method: 'POST' }),
    onSuccess: () => { setSigned(true); setErrMsg(''); refetch(); },
    onError: (e: any) => setErrMsg(e?.message ?? '签到失败'),
  });

  return (
    <div className="mx-auto max-w-md px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black text-center mb-8">每日签到</h1>

      <GlassCard className="p-8 text-center">
        <div className="text-6xl mb-4">{signed || status?.signed ? '✅' : '🎁'}</div>
        <h2 className="text-lg font-medium text-ink-black">
          {status?.signed ? '今日已签到' : signed ? '签到成功!' : '签到领积分'}
        </h2>
        <p className="text-sm text-warm-600 mt-2">
          {status?.continuous_days ? `已连续签到 ${status.continuous_days} 天` : '每天签到，积分翻倍'}
        </p>
        {status?.earned_points !== undefined && status.earned_points > 0 && (
          <p className="mt-1 text-accent-gold text-sm">本次获得 {status.earned_points} 积分</p>
        )}

        {!status?.signed && !signed && (
          <Button variant="gold" size="lg" className="mt-6 w-full" loading={signMutation.isPending}
            onClick={() => signMutation.mutate()}>
            签到领积分
          </Button>
        )}
        {errMsg && <p className="mt-3 text-sm text-danger text-center">{errMsg}</p>}

        {/* 模拟连续签到日历 */}
        <div className="mt-8 grid grid-cols-7 gap-2">
          {Array.from({ length: 7 }).map((_, i) => (
            <div key={i} className={`w-9 h-9 rounded-full flex items-center justify-center text-xs ${
              i < (status?.continuous_days ?? 0) % 7
                ? 'bg-accent-green text-white'
                : 'bg-warm-100 text-warm-400'
            }`}>
              {i + 1}
            </div>
          ))}
        </div>
        <p className="text-xs text-warm-400 mt-3">本周签到</p>
      </GlassCard>
    </div>
  );
}
