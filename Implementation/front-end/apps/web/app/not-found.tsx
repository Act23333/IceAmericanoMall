import { GlassCard } from '@icedmall/ui';

/**
 * 自定义 404 — 品牌风格
 */
export default function NotFound() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-mist-white px-4">
      <GlassCard className="max-w-md p-12 text-center">
        <div className="text-6xl select-none">🍃</div>
        <h2 className="mt-6 text-xl font-light text-ink-black">
          页面不存在
        </h2>
        <p className="mt-3 text-sm text-warm-600 leading-relaxed">
          你寻找的页面可能已搬家、下架，或从未存在过。
          <br />
          就像风中的落叶，不如随风而去。
        </p>
        <div className="mt-8 flex items-center justify-center gap-4">
          <a
            href="/"
            className="text-sm font-medium text-accent-green hover:text-accent-green-light transition-colors"
          >
            返回首页
          </a>
          <span className="text-warm-200">|</span>
          <a
            href="/marketplace"
            className="text-sm font-medium text-accent-green hover:text-accent-green-light transition-colors"
          >
            去逛商城
          </a>
        </div>
      </GlassCard>
    </div>
  );
}
