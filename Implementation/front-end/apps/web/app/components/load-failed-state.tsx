'use client';

import { useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { LoadFailed } from '@icedmall/ui';

export interface LoadFailedStateProps {
  /** 主标题（默认「加载失败」）。 */
  title?: string;
  /** 副文案 —— 必须让用户分清「加载失败」与「没有数据」。 */
  description?: string;
  /** 技术细节（错误文案），可选。 */
  detail?: string;
  /** 紧凑态：区块级失败（评价区 / 店铺卡）用，不抢主内容。 */
  compact?: boolean;
  /**
   * 重试动作。**默认 `router.refresh()`** —— 服务端组件页面（商城首页 / 商品详情）
   * 无法直接把函数传给客户端组件，故由本封装提供缺省值；
   * 客户端页面应当显式传自己的 `refetch`（react-query 语义更准，且不会整页重渲染）。
   */
  onRetry?: () => void;
  /** 重试进行中。 */
  retrying?: boolean;
  className?: string;
}

/**
 * Web 侧失败态 —— 对 `@icedmall/ui` 的 `LoadFailed` 补一个缺省「重试」动作。
 *
 * 为什么分两层：`packages/ui` 保持**纯展示**（不引 next/navigation，可被任何宿主复用）；
 * 重试的宿主语义（`router.refresh()` / `queryClient.refetch` / 原生页面重载）在应用层注入。
 */
export function LoadFailedState({
  onRetry,
  ...rest
}: LoadFailedStateProps) {
  const router = useRouter();
  const defaultRetry = useCallback(() => router.refresh(), [router]);
  return <LoadFailed {...rest} onRetry={onRetry ?? defaultRetry} />;
}
