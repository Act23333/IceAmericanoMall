/**
 * 全局加载状态 — 品牌风格骨架屏
 *
 * 无 spinner，使用玻璃微光脉冲，与设计语言一致
 */
export default function Loading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-mist-white">
      <div className="flex flex-col items-center gap-4">
        {/* 玻璃方块 — 品牌加载指示器 */}
        <div className="h-12 w-12 rounded-2xl border border-white/20 bg-white/10 backdrop-blur-md animate-glass-shimmer" />
        <p className="text-sm text-warm-400 animate-fade-in">
          正在准备…
        </p>
      </div>
    </div>
  );
}
