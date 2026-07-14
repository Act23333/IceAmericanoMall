'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useCreateAfterSale } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';
import Link from 'next/link';

export default function AfterSalePage() {
  const params = useSearchParams();
  const orderNo = params.get('orderNo') ?? '';
  const router = useRouter();
  const create = useCreateAfterSale();
  const [type, setType] = useState(1);
  const [reason, setReason] = useState('');
  const [amount, setAmount] = useState('');
  const [msg, setMsg] = useState('');

  const handleSubmit = () => {
    if (!reason.trim()) { setMsg('请填写退货/退款原因'); return; }
    create.mutate(
      { orderNo, type, reason, refundAmount: amount ? Number(amount) * 100 : undefined },
      { onSuccess: () => router.push('/shop/orders'),
        onError: (e: any) => setMsg(e?.message ?? '提交失败') },
    );
  };

  return (
    <div className="mx-auto max-w-md px-4 pt-24 pb-20">
      <h1 className="text-xl font-light text-ink-black mb-6">申请售后</h1>
      <GlassCard className="p-6 space-y-4" blur="sm">
        <div>
          <label className="text-sm text-ink-soft mb-1.5 block">订单号</label>
          <input value={orderNo} readOnly className="w-full rounded-xl border border-warm-200 bg-warm-50 px-4 py-2.5 text-sm" />
        </div>
        <div>
          <label className="text-sm text-ink-soft mb-1.5 block">售后类型</label>
          <div className="flex gap-2">
            {[{v:1,l:'退货退款'},{v:2,l:'仅退款'}].map(t => (
              <button key={t.v} onClick={() => setType(t.v)}
                className={`flex-1 py-2 text-sm rounded-xl border ${type===t.v?'border-accent-green bg-accent-green/5 text-accent-green':'border-warm-200 text-warm-600'}`}>{t.l}</button>
            ))}
          </div>
        </div>
        <div>
          <label className="text-sm text-ink-soft mb-1.5 block">退款金额（元，可选）</label>
          <input type="number" value={amount} onChange={e => setAmount(e.target.value)} placeholder="0.00" min="0" step="0.01"
            className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm" />
        </div>
        <div>
          <label className="text-sm text-ink-soft mb-1.5 block">原因说明</label>
          <textarea value={reason} onChange={e => setReason(e.target.value)} rows={3} placeholder="请描述退货/退款原因"
            className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm" />
        </div>
        {msg && <p className="text-sm text-center text-danger">{msg}</p>}
        <Button variant="primary" size="lg" className="w-full" loading={create.isPending} onClick={handleSubmit}>提交申请</Button>
      </GlassCard>
      <Link href="/shop/orders" className="mt-4 block text-center text-xs text-warm-400">返回订单列表</Link>
    </div>
  );
}
