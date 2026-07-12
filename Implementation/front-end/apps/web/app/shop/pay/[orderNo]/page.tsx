'use client';

import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import Link from 'next/link';
import {
  useOrder,
  useInitiatePay,
  useBalance,
  useRecharge,
  ApiError,
  PAY_CHANNEL_TEXT,
  type PayChannel,
} from '@icedmall/api';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import { formatPrice, yuanToCents } from '@icedmall/utils';

const CHANNELS: PayChannel[] = ['WECHAT', 'ALIPAY', 'BALANCE'];
const CHANNEL_ICON: Record<PayChannel, string> = { WECHAT: '💚', ALIPAY: '💙', BALANCE: '👛' };

export default function PayPage() {
  const { orderNo } = useParams() as { orderNo: string };
  const router = useRouter();
  const { data: order, isLoading } = useOrder(orderNo);
  const { data: balance } = useBalance();
  const initiate = useInitiatePay();
  const recharge = useRecharge();

  const [channel, setChannel] = useState<PayChannel>('WECHAT');
  const [payUrl, setPayUrl] = useState<string>();
  const [error, setError] = useState<string>();
  const [rechargeYuan, setRechargeYuan] = useState('');

  if (isLoading) {
    return <div className="mx-auto max-w-md px-4 pt-24 pb-20"><div className="h-64 rounded-2xl bg-warm-100 animate-glass-shimmer" /></div>;
  }
  if (!order) {
    return (
      <div className="text-center pt-24 pb-20">
        <div className="text-6xl">🔍</div><h2 className="mt-4 text-lg text-ink-black">订单未找到</h2>
        <Link href="/shop/orders" className="mt-4 inline-block text-sm text-accent-green">返回订单列表</Link>
      </div>
    );
  }
  if (order.status !== 1) {
    return (
      <div className="text-center pt-24 pb-20">
        <div className="text-6xl">✅</div>
        <h2 className="mt-4 text-lg text-ink-black">该订单无需支付</h2>
        <Link href={`/shop/orders/${orderNo}`} className="mt-4 inline-block text-sm text-accent-green">查看订单详情</Link>
      </div>
    );
  }

  const insufficient = channel === 'BALANCE' && (balance ?? 0) < order.payAmount;

  const handlePay = () => {
    setError(undefined);
    setPayUrl(undefined);
    initiate.mutate(
      { orderNo, channel },
      {
        onSuccess: (pay) => {
          if (pay.status === 3) {
            router.push(`/shop/orders/${orderNo}`); // 余额支付即时完成
          } else if (pay.qrCodeUrl) {
            setPayUrl(pay.qrCodeUrl); // 微信/支付宝返回支付链接
          }
        },
        onError: (e) => setError(e instanceof ApiError ? e.message : '支付失败，请重试'),
      },
    );
  };

  const handleRecharge = () => {
    const cents = yuanToCents(Number(rechargeYuan));
    if (cents > 0) recharge.mutate(cents, { onSuccess: () => setRechargeYuan('') });
  };

  return (
    <div className="mx-auto max-w-md px-4 pt-24 pb-20">
      <h1 className="text-xl font-light text-ink-black mb-6">收银台</h1>

      <GlassCard className="p-4 mb-4" blur="sm">
        <p className="text-xs text-warm-400">订单号: {order.orderNo}</p>
        <div className="flex items-baseline justify-between mt-3">
          <span className="text-sm text-warm-600">应付金额</span>
          <PriceDisplay cents={order.payAmount} size="lg" />
        </div>
      </GlassCard>

      <GlassCard className="p-4 mb-4" blur="sm">
        <h3 className="text-sm font-medium text-ink-black mb-3">选择支付方式</h3>
        <div className="space-y-2">
          {CHANNELS.map((c) => (
            <button
              key={c}
              onClick={() => setChannel(c)}
              className={`flex w-full items-center justify-between rounded-xl border px-4 py-3 text-left transition ${
                channel === c ? 'border-accent-green bg-accent-green/5' : 'border-warm-200'
              }`}
            >
              <span className="flex items-center gap-2 text-sm text-ink-black">
                <span>{CHANNEL_ICON[c]}</span>{PAY_CHANNEL_TEXT[c]}
                {c === 'BALANCE' && (
                  <span className="text-xs text-warm-400">（余额 ¥{formatPrice(balance ?? 0)}）</span>
                )}
              </span>
              <span className={`h-4 w-4 rounded-full border ${channel === c ? 'border-accent-green bg-accent-green' : 'border-warm-300'}`} />
            </button>
          ))}
        </div>
      </GlassCard>

      {insufficient && (
        <GlassCard className="p-4 mb-4" blur="sm">
          <p className="text-sm text-danger mb-2">余额不足，请先充值</p>
          <div className="flex gap-2">
            <input
              type="number"
              value={rechargeYuan}
              onChange={(e) => setRechargeYuan(e.target.value)}
              placeholder="充值金额（元）"
              className="flex-1 rounded-lg border border-warm-200 px-3 py-2 text-sm"
            />
            <Button variant="secondary" size="md" loading={recharge.isPending} onClick={handleRecharge}>充值</Button>
          </div>
        </GlassCard>
      )}

      {payUrl && (
        <GlassCard className="p-4 mb-4 text-center" blur="sm">
          <p className="text-sm text-ink-black mb-2">{PAY_CHANNEL_TEXT[channel]}支付链接已生成</p>
          <a href={payUrl} target="_blank" rel="noreferrer" className="text-xs break-all text-accent-green underline">{payUrl}</a>
          <p className="mt-2 text-xs text-warm-400">（演示环境：支付完成后由回调更新订单状态）</p>
        </GlassCard>
      )}

      {error && <p className="mb-3 text-center text-sm text-danger">{error}</p>}

      <Button
        variant="primary"
        size="lg"
        className="w-full"
        loading={initiate.isPending}
        disabled={insufficient}
        onClick={handlePay}
      >
        {channel === 'BALANCE' ? '余额支付' : `使用${PAY_CHANNEL_TEXT[channel]}`}
      </Button>

      <Link href={`/shop/orders/${orderNo}`} className="mt-4 block text-center text-xs text-warm-400">稍后支付，返回订单</Link>
    </div>
  );
}
