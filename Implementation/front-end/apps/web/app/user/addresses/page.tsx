'use client';

import { useState } from 'react';
import { useAddresses, useAddAddress, useUpdateAddress, useDeleteAddress, type AddressReq } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';

const EMPTY: AddressReq = {
  receiver: '', phone: '', province: '', city: '', district: '', street: '', detail: '',
  defaulted: false, label: '',
};

export default function AddressesPage() {
  const { data: addresses } = useAddresses();
  const addAddress = useAddAddress();
  const updateAddress = useUpdateAddress();
  const deleteAddress = useDeleteAddress();

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<AddressReq>({ ...EMPTY });
  const [msg, setMsg] = useState('');

  const set = (field: keyof AddressReq, value: string) => setForm(f => ({ ...f, [field]: value }));

  const startEdit = (addr: any) => {
    setEditingId(addr.id);
    setForm({ receiver: addr.receiver, phone: addr.phone, province: addr.province, city: addr.city,
      district: addr.district, street: addr.street || '', detail: addr.detail,
      defaulted: addr.defaulted, label: addr.label || '' });
    setShowForm(true);
  };

  const handleSave = async () => {
    if (!form.receiver || !form.phone || !form.province || !form.detail) {
      setMsg('请填写完整信息'); return;
    }
    try {
      if (editingId) {
        await updateAddress.mutateAsync({ ...form, id: editingId } as any);
      } else {
        await addAddress.mutateAsync(form);
      }
      setShowForm(false); setEditingId(null); setForm({ ...EMPTY }); setMsg('');
    } catch (e: any) { setMsg(e?.message ?? '保存失败'); }
  };

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-light text-ink-black">收货地址</h1>
        <Button size="sm" variant="primary" onClick={() => { setShowForm(!showForm); setEditingId(null); setForm({ ...EMPTY }); }}>
          {showForm ? '取消' : '+ 新增地址'}
        </Button>
      </div>

      {showForm && (
        <GlassCard className="p-4 mb-6 space-y-3" blur="sm">
          <div className="grid grid-cols-2 gap-3">
            <input value={form.receiver} onChange={e => set('receiver', e.target.value)} placeholder="收货人" className="rounded-xl border border-warm-200 px-3 py-2 text-sm" />
            <input value={form.phone} onChange={e => set('phone', e.target.value)} placeholder="手机号" className="rounded-xl border border-warm-200 px-3 py-2 text-sm" />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <input value={form.province} onChange={e => set('province', e.target.value)} placeholder="省" className="rounded-xl border border-warm-200 px-3 py-2 text-sm" />
            <input value={form.city} onChange={e => set('city', e.target.value)} placeholder="市" className="rounded-xl border border-warm-200 px-3 py-2 text-sm" />
            <input value={form.district} onChange={e => set('district', e.target.value)} placeholder="区" className="rounded-xl border border-warm-200 px-3 py-2 text-sm" />
          </div>
          <input value={form.street} onChange={e => set('street', e.target.value)} placeholder="街道" className="w-full rounded-xl border border-warm-200 px-3 py-2 text-sm" />
          <input value={form.detail} onChange={e => set('detail', e.target.value)} placeholder="详细地址" className="w-full rounded-xl border border-warm-200 px-3 py-2 text-sm" />
          {msg && <p className="text-xs text-danger">{msg}</p>}
          <Button variant="primary" size="md" className="w-full" onClick={handleSave} loading={addAddress.isPending || updateAddress.isPending}>
            {editingId ? '保存修改' : '添加地址'}
          </Button>
        </GlassCard>
      )}

      {(!addresses || addresses.length === 0) && !showForm && (
        <p className="text-center text-sm text-warm-400 py-20">暂无收货地址</p>
      )}

      {addresses?.map((addr) => (
        <GlassCard key={addr.id} className="p-4 mb-3" blur="sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm font-medium text-ink-black">{addr.receiver} {addr.phone}</p>
              <p className="text-xs text-warm-600 mt-1">{addr.province} {addr.city} {addr.district} {addr.street} {addr.detail}</p>
              {addr.label && <span className="text-xs text-accent-gold mt-1 inline-block">{addr.label}</span>}
            </div>
            <div className="flex gap-2 text-xs">
              <button onClick={() => startEdit(addr)} className="text-accent-green">编辑</button>
              <button onClick={() => deleteAddress.mutate(addr.id)} className="text-danger">删除</button>
            </div>
          </div>
          {addr.defaulted && <span className="text-xs text-accent-green mt-2 block">默认地址</span>}
        </GlassCard>
      ))}
    </div>
  );
}
