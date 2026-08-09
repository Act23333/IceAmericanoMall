'use client';

import { useEffect } from 'react';

/** 浏览历史记录 — 客户端组件，页面加载时自动记录 */
export function TrackHistory({ productId }: { productId: number }) {
  useEffect(() => {
    const base = process.env.NEXT_PUBLIC_API_URL || '';
    fetch(`${base}/api/user/history?productId=${productId}`, {
      method: 'POST',
      credentials: 'include',
    }).catch(() => {});
  }, [productId]);
  return null;
}
