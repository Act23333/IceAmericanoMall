import { cn } from '../lib/utils';

interface GlassCardProps {
  children: React.ReactNode;
  className?: string;
  /** blur 强度: 'sm' = 12px | 'md' = 20px | 'lg' = 32px */
  blur?: 'sm' | 'md' | 'lg';
  /** 是否 hover 时增强玻璃效果 */
  hover?: boolean;
  /** 品牌暗色模式 (品牌主页使用) */
  dark?: boolean;
  /** 可选的内联 style */
  style?: React.CSSProperties;
}

const blurMap: Record<NonNullable<GlassCardProps['blur']>, string> = {
  sm: 'backdrop-blur-md',
  md: 'backdrop-blur-xl',
  lg: 'backdrop-blur-2xl',
};

/**
 * 毛玻璃卡片 — 可复用的玻璃容器
 *
 * 视觉规范: 17-Design-System §4.1
 *   默认: bg-white/70 backdrop-blur-[20px] saturate-[180%] border-white/30 rounded-2xl
 *   hover: bg-white/85 -translate-y-0.5 shadow-lg
 *   dark: 品牌主页暗色玻璃
 *
 * 用途：品牌页信息卡片、认证表单、弹窗、商品卡片容器
 */
export function GlassCard({
  children,
  className,
  blur = 'md',
  hover = false,
  dark = false,
  style,
}: GlassCardProps) {
  return (
    <div
      className={cn(
        'rounded-2xl border',
        dark
          ? 'bg-black/40 border-white/10'
          : 'bg-white/70 border-white/30',
        blurMap[blur],
        'backdrop-saturate-[180%]',
        'shadow-[0_8px_32px_rgba(0,0,0,0.06)]',
        hover && [
          'transition-all duration-300 ease-out cursor-pointer',
          dark
            ? 'hover:bg-black/60 hover:shadow-[0_12px_40px_rgba(0,0,0,0.25)]'
            : 'hover:bg-white/85 hover:-translate-y-0.5 hover:shadow-[0_12px_40px_rgba(0,0,0,0.10)]',
        ],
        className,
      )}
      style={style}
    >
      {children}
    </div>
  );
}
