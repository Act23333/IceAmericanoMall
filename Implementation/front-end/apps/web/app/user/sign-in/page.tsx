'use client';

import { useQuery, useMutation } from '@tanstack/react-query';
import { apiClient } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';

export default function SignInPage() {
  const [signed, setSigned] = useState(false);

  const { data: status, refetch } = useQuery({
    queryKey: ['sign', 'status'],
    queryFn: () => apiClient<{ signed: boolean; continuousDays: number; points: number }>('/api/user/sign/status'),
  });

  const signMutation = useMutation({
    mutationFn: () => apiClient<{ points: number }>('/api/user/sign', { method: 'POST' }),
    onSuccess: () => { setSigned(true); refetch(); },
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
          {status?.continuousDays ? `已连续签到 ${status.continuousDays} 天` : '每天签到，积分翻倍'}
        </p>
        {status?.points !== undefined && <p className="mt-1 text-accent-gold text-sm">当前积分: {status.points}</p>}

        {!status?.signed && !signed && (
          <Button variant="gold" size="lg" className="mt-6 w-full" loading={signMutation.isPending}
            onClick={() => signMutation.mutate()}>
            签到领积分
          </Button>
        )}

        {/* 模拟连续签到日历 */}
        <div className="mt-8 grid grid-cols-7 gap-2">
          {Array.from({ length: 7 }).map((_, i) => (
            <div key={i} className={`w-9 h-9 rounded-full flex items-center justify-center text-xs ${
              i < (status?.continuousDays ?? 0) % 7
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
