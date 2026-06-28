'use client';

import { Button, GlassCard } from '@icedmall/ui';

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="zh-CN">
      <body className="bg-mist-white">
        <div className="flex min-h-screen items-center justify-center px-4">
          <GlassCard className="max-w-md p-12 text-center">
            <div className="text-6xl select-none">🌊</div>
            <h2 className="mt-6 text-xl font-light text-ink-black">出现了一些问题</h2>
            <p className="mt-3 text-sm text-warm-600 leading-relaxed">
              页面遇到了意外错误。请重试，或返回首页继续浏览。
            </p>
            <div className="mt-8 flex items-center justify-center gap-4">
              <Button onClick={reset} variant="primary" size="sm">
                重试
              </Button>
              <a
                href="/"
                className="text-sm font-medium text-accent-green hover:text-accent-green-light transition-colors"
              >
                返回首页
              </a>
            </div>
          </GlassCard>
        </div>
      </body>
    </html>
  );
}
