'use client';

import { forwardRef } from 'react';
import { cn } from '../lib/utils';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'gold';
type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}

/**
 * 设计系统按钮 — 品牌风格
 *
 * primary: 绿色填充 (CTA)
 * secondary: 毛玻璃描边
 * ghost: 透明文字
 * gold: 金色填充 (高级CTA, 如下单)
 *
 * GPU-friendly: active:scale-[0.98], 无 layout shift
 */
const variants: Record<ButtonVariant, string> = {
  primary:
    'bg-accent-green text-white shadow-sm shadow-accent-green/10 ' +
    'hover:bg-accent-green-light hover:shadow-md hover:shadow-accent-green/15 ' +
    'active:bg-accent-green-dark',
  secondary:
    'glass border border-white/30 text-ink-black ' +
    'hover:bg-white/30 hover:shadow-md ' +
    'active:bg-white/20',
  ghost:
    'bg-transparent text-ink-soft ' +
    'hover:bg-warm-100 hover:text-ink-black ' +
    'active:bg-warm-200',
  gold:
    'bg-accent-gold text-white shadow-sm shadow-accent-gold/15 ' +
    'hover:bg-accent-gold-light hover:shadow-md hover:shadow-accent-gold/20 ' +
    'active:bg-accent-gold',
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
