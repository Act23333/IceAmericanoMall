'use client';

import { forwardRef } from 'react';
import { cn } from '../lib/utils';

type ButtonVariant = 'primary' | 'secondary' | 'glass' | 'ghost' | 'gold';
type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}

/**
 * 设计系统按钮 — 17-Design-System §4.5
 *
 * primary: 青绿填充 (#2D8B6E) — 主 CTA
 * glass: 毛玻璃描边 — 次要操作
 * ghost: 透明文字 — 低优先级
 * gold: 金微光填充 (#C8A96E) — 高级 CTA (下单/品牌主页)
 *
 * GPU-friendly: active:scale-[0.98], 无 layout shift
 */
const variants: Record<ButtonVariant, string> = {
  primary:
    'bg-accent text-white shadow-sm shadow-accent/10 ' +
    'hover:bg-accent-hover hover:shadow-md hover:shadow-accent/15 ' +
    'active:bg-accent-green-dark',
  secondary:
    'bg-white/70 backdrop-blur-[20px] backdrop-saturate-[180%] border border-white/30 text-ink-black ' +
    'hover:bg-white/60 hover:shadow-md ' +
    'active:bg-white/40',
  glass:
    'bg-white/70 backdrop-blur-[20px] backdrop-saturate-[180%] border border-white/30 text-ink-black ' +
    'hover:bg-white/60 hover:shadow-md ' +
    'active:bg-white/40',
  ghost:
    'bg-transparent text-text-secondary ' +
    'hover:bg-accent-light hover:text-accent ' +
    'active:bg-accent-light/50',
  gold:
    'bg-gold text-white shadow-sm shadow-gold/15 ' +
    'hover:bg-gold/80 hover:shadow-md hover:shadow-gold/20 ' +
    'active:bg-gold',
};

const sizes: Record<ButtonSize, string> = {
  sm: 'px-4 py-1.5 text-xs rounded-xl gap-1.5',
  md: 'px-5 py-2.5 text-sm rounded-xl gap-2',
  lg: 'px-7 py-3 text-sm rounded-2xl gap-2',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', loading, className, children, disabled, ...props }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={cn(
          'inline-flex items-center justify-center font-medium',
          'transition-all duration-200',
          'active:scale-[0.98]',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-green/30 focus-visible:ring-offset-2',
          'disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100',
          variants[variant],
          sizes[size],
          className,
        )}
        {...props}
      >
        {loading && (
          <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        )}
        {children}
      </button>
    );
  },
);

Button.displayName = 'Button';
