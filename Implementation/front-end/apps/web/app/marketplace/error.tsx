'use client';

import { Button, GlassCard } from '@icedmall/ui';

export default function MarketplaceError({
  error: _error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="flex min-h-[60vh] items-center justify-center px-4 pt-24">
      <GlassCard className="max-w-md p-10 text-center">
        <div className="text-4xl select-none">☁️</div>
        <h2 className="mt-4 text-lg font-medium text-ink-black">
          加载失败
        </h2>
        <p className="mt-2 text-sm text-warm-600">
          商城数据暂时无法获取，请稍后重试。
        </p>
        <Button onClick={reset} variant="primary" size="sm" className="mt-6">
          重新加载
        </Button>
      </GlassCard>
    </div>
  );
}
