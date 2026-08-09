'use client';

import { useEffect } from 'react';
import { apiClient } from '@icedmall/api';

/** 浏览历史记录 — 客户端组件，页面加载时自动记录 */
export function TrackHistory({ productId }: { productId: number }) {
  useEffect(() => {
    apiClient(`/api/user/history?productId=${productId}`, { method: 'POST' }).catch(() => {});
  }, [productId]);
  return null;
}
