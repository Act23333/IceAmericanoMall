import { formatPrice } from '@icedmall/utils';
import { cn } from '../lib/utils';

interface PriceDisplayProps {
  /** 价格（分） */
  cents: number;
  /** 尺寸 */
  size?: 'sm' | 'md' | 'lg';
  /** 划线原价（分），不传则不显示 */
  originalCents?: number;
  className?: string;
}

const sizeClasses = {
  sm: 'text-sm',
  md: 'text-lg',
  lg: 'text-2xl',
};

/**
 * 价格展示 — 统一价格渲染
 *
 * 始终使用 formatPrice() 将分转为元显示
 * 不做任何手动 /100 运算
 */
export function PriceDisplay({
  cents,
  size = 'md',
  originalCents,
  className,
}: PriceDisplayProps) {
  return (
    <span className={cn('inline-flex items-baseline gap-1.5', className)}>
      <span className={cn('font-semibold text-ink-black', sizeClasses[size])}>
        ¥{formatPrice(cents)}
      </span>
      {originalCents !== undefined && originalCents > cents && (
        <span className="text-sm text-warm-400 line-through">
          ¥{formatPrice(originalCents)}
        </span>
      )}
    </span>
  );
}
