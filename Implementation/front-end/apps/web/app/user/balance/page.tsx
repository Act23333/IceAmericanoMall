'use client';

import { useState } from 'react';
import { useBalance, useRecharge } from '@icedmall/api';
import { GlassCard, Button, PriceDisplay } from '@icedmall/ui';
import { formatPrice, yuanToCents } from '@icedmall/utils';
import { Wallet, Plus } from 'lucide-react';

/**
 * 余额中心 — V5.0
 *
 * 展示: 账户余额 + 简易充值(Mock)
 */
export default function BalancePage() {
  const { data: balance, isLoading } = useBalance();
  const recharge = useRecharge();
  const [amountYuan, setAmountYuan] = useState('');
  const [rechargeMsg, setRechargeMsg] = useState('');

  const handleRecharge = () => {
    const cents = yuanToCents(Number(amountYuan));
    if (cents <= 0) {
      setRechargeMsg('请输入有效金额');
      return;
    }
    recharge.mutate(cents, {
      onSuccess: (newBalance) => {
        setRechargeMsg(`充值成功！当前余额 ¥${formatPrice(newBalance as number)}`);
        setAmountYuan('');
      },
      onError: (e: any) => setRechargeMsg(e?.message ?? '充值失败'),
    });
  };

  const presetAmounts = [50, 100, 200, 500];

  return (
    <div className="mx-auto max-w-2xl px-4 pt-8 pb-20">
      <h1 className="text-xl font-light text-ink-black mb-6">我的余额</h1>

      {/* 余额卡片 */}
      <GlassCard className="p-6 mb-6 text-center" blur="md">
        <div className="flex items-center justify-center gap-2 mb-2">
          <Wallet className="h-5 w-5 text-accent" />
          <span className="text-sm text-text-secondary">账户余额</span>
        </div>
        {isLoading ? (
          <div className="h-10 w-32 mx-auto rounded bg-warm-gray-100 animate-pulse" />
        ) : (
          <PriceDisplay cents={balance ?? 0} size="lg" className="justify-center" />
        )}
      </GlassCard>

      {/* 充值 */}
      <GlassCard className="p-5" blur="md">
        <h2 className="text-sm font-medium text-text-secondary mb-4 flex items-center gap-1.5">
          <Plus className="h-4 w-4" /> 余额充值 (演示)
        </h2>

        {/* 快捷金额 */}
        <div className="flex flex-wrap gap-2 mb-4">
          {presetAmounts.map((a) => (
            <button
              key={a}
              onClick={() => setAmountYuan(String(a))}
              className={`px-4 py-2 text-sm rounded-xl border transition-all duration-200 ${
                amountYuan === String(a)
                  ? 'border-accent bg-accent-light text-accent'
                  : 'border-warm-gray-200 hover:border-warm-gray-400 text-text-secondary'
              }`}
            >
              ¥{a}
            </button>
          ))}
        </div>

        {/* 自定义金额 */}
        <div className="flex gap-2">
          <input
            type="number"
            value={amountYuan}
            onChange={(e) => setAmountYuan(e.target.value)}
            placeholder="输入充值金额（元）"
            min="1"
            className="flex-1 rounded-xl border border-warm-gray-200 bg-white px-4 py-2.5 text-sm outline-none transition-all focus:border-accent focus:ring-2 focus:ring-accent/20 placeholder:text-text-tertiary"
          />
          <Button
            variant="primary"
            size="md"
            loading={recharge.isPending}
            onClick={handleRecharge}
          >
            充值
          </Button>
        </div>

        {rechargeMsg && (
          <p className={`mt-3 text-sm text-center ${rechargeMsg.includes('成功') ? 'text-accent' : 'text-danger'}`}>
            {rechargeMsg}
          </p>
        )}

        <p className="mt-4 text-xs text-text-tertiary text-center">
          演示环境，充值即时到账，不经过真实支付渠道
        </p>
      </GlassCard>
    </div>
  );
}
