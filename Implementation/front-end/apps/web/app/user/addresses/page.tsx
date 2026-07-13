'use client';

import { useState } from 'react';
import { useAddresses, useDeleteAddress, type AddressReq } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';

const EMPTY_ADDR: AddressReq = {
  receiver: '', phone: '', province: '', city: '', district: '', street: '', detail: '',
  defaulted: false, label: '',
};

export default function AddressesPage() {
  const { data: addresses } = useAddresses();
  const deleteAddress = useDeleteAddress();

  const [editing, setEditing] = useState<(AddressReq & { id?: number }) | null>(null);
  const [form, setForm] = useState<AddressReq>({ ...EMPTY_ADDR });

  const startEdit = (addr: any) => {
    setEditing({ id: addr.id, receiver: addr.receiver, phone: addr.phone, province: addr.province,
      city: addr.city, district: addr.district, street: addr.street || '', detail: addr.detail,
      defaulted: addr.defaulted, label: addr.label || '' });
    setForm({ receiver: addr.receiver, phone: addr.phone, province: addr.province,
      city: addr.city, district: addr.district, street: addr.street || '', detail: addr.detail,
      defaulted: addr.defaulted, label: addr.label || '' });
  };

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-light text-ink-black">收货地址</h1>
        <Button size="sm" variant="primary" onClick={() => { setEditing(null); setForm({ ...EMPTY_ADDR }); }}>+ 新增地址</Button>
      </div>

      {/* 编辑表单 */}
      {editing !== null || (!editing && form.receiver === '' && addresses?.length === 0) ? null : null}

      {/* 地址列表 */}
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
