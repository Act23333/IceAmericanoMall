import { GlassCard } from '@icedmall/ui';
import Link from 'next/link';

/**
 * SPU 详情段的 404 —— **「商品不存在/已删除」专用文案**
 * （2026-10-18 B8；对应后端 P2 的新错误语义）
 *
 * 为什么单独建一个段级 `not-found.tsx`，而不是复用 `app/not-found.tsx`：
 *   ① 「页面不存在」（用户拼错 URL）与「商品不存在/已删除」（链路里带了个失效的
 *      `spu_id`）是**两种不同事实**，文案与下一步动作不同 —— 后者要给「回商城逛逛」；
 *   ② 段级 404 文件让 `notFound()` 返回**真实 HTTP 404 状态**（利于 SEO 与分享链路），
 *      而内联渲染一段「商品不存在」的 HTML 会是 **200**（软 404）。
 *
 * 触发点：`page.tsx` 在 `isNotFound(e)` 为真（HTTP 404 / `1103` / 存量 `3011`）时调用
 * `notFound()`；其它错误走 `error.tsx`（「暂时无法加载」+ 重试），两者不混。
 */
export default function SpuNotFound() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-mist-white px-4">
      <GlassCard className="max-w-md p-12 text-center">
        <div className="text-6xl select-none">🍃</div>
        <h2 className="mt-6 text-xl font-light text-ink-black">商品不存在或已删除</h2>
        <p className="mt-3 text-sm text-warm-600 leading-relaxed">
          这件商品可能已被商家删除，或链接里的标识已失效。
          <br />
          换一件看看吧。
        </p>
        <div className="mt-8 flex items-center justify-center gap-4">
          <Link
            href="/marketplace"
            className="text-sm font-medium text-accent-green hover:text-accent-green-light transition-colors"
          >
            去逛商城
          </Link>
          <span className="text-warm-200">|</span>
          <Link
            href="/"
            className="text-sm font-medium text-accent-green hover:text-accent-green-light transition-colors"
          >
            返回首页
          </Link>
        </div>
      </GlassCard>
    </div>
  );
}
