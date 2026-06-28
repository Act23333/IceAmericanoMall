import { cn } from '../lib/utils';

interface GlassCardProps {
  children: React.ReactNode;
  className?: string;
  /** blur 强度: 'sm' = 12px | 'md' = 20px | 'lg' = 32px */
  blur?: 'sm' | 'md' | 'lg';
  /** 是否 hover 时增强玻璃效果 */
  hover?: boolean;
  /** 可选的内联 style */
  style?: React.CSSProperties;
}

const blurClasses: Record<NonNullable<GlassCardProps['blur']>, string> = {
  sm: 'backdrop-blur-md',
  md: 'backdrop-blur-xl',
  lg: 'backdrop-blur-2xl',
};

/**
 * 毛玻璃卡片 — 可复用的玻璃容器
 *
 * 用途：品牌页信息卡片、认证表单、弹窗
 */
export function GlassCard({
  children,
  className,
  blur = 'md',
  hover = false,
  style,
}: GlassCardProps) {
  return (
    <div
      className={cn(
        'rounded-2xl border border-white/20 bg-white/[0.08]',
        blurClasses[blur],
        'shadow-glass',
        hover && 'transition-all duration-300 hover:shadow-glass-lg hover:bg-white/[0.12]',
        className,
      )}
      style={style}
    >
      {children}
    </div>
  );
}
