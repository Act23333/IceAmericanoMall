/**
 * 网站页脚 — Server Component
 *
 * 品牌风格：深色背景 + 玻璃分割线
 */
export function Footer() {
  return (
    <footer className="bg-ink-black text-warm-400">
      <div className="mx-auto max-w-7xl px-6 py-16">
        <div className="grid gap-12 md:grid-cols-4">
          {/* 品牌 */}
          <div className="space-y-3">
            <h3 className="text-white text-base font-medium flex items-center gap-2">
              <span className="text-accent-green">◆</span>
              冰美商城
            </h3>
            <p className="text-sm leading-relaxed">
              Technology meets everyday life.
              <br />
              科技自然融入生活。
            </p>
          </div>

          {/* 探索 */}
          <div className="space-y-3">
            <h4 className="text-white text-sm font-medium">探索</h4>
            <div className="space-y-2">
              <a href="/marketplace" className="block text-sm hover:text-white transition-colors">商城</a>
              <a href="/marketplace?sort=newest" className="block text-sm hover:text-white transition-colors">新品</a>
            </div>
          </div>

          {/* 帮助 */}
          <div className="space-y-3">
            <h4 className="text-white text-sm font-medium">帮助</h4>
            <div className="space-y-2">
              <a href="/help/faq" className="block text-sm hover:text-white transition-colors">常见问题</a>
              <a href="/help/shipping" className="block text-sm hover:text-white transition-colors">物流说明</a>
              <a href="/help/returns" className="block text-sm hover:text-white transition-colors">退换政策</a>
            </div>
          </div>

          {/* 法律 */}
          <div className="space-y-3">
            <h4 className="text-white text-sm font-medium">法律</h4>
            <div className="space-y-2">
              <a href="/legal/privacy" className="block text-sm hover:text-white transition-colors">隐私政策</a>
              <a href="/legal/terms" className="block text-sm hover:text-white transition-colors">服务条款</a>
            </div>
          </div>
        </div>

        {/* 玻璃分割线 */}
        <div className="mt-12 pt-8 border-t border-white/10">
          <p className="text-center text-xs text-warm-600">
            &copy; {new Date().getFullYear()} 冰美商城 IceAmericanoMall. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
}
