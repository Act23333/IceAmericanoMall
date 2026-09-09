'use client';

import { Minus, Plus } from 'lucide-react';
import { cn } from '../lib/utils';

interface QuantityStepperProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  className?: string;
}

/**
 * 数量步进器 — 京东风格 ± 控件
 */
export function QuantityStepper({
  value,
  onChange,
  min = 1,
  max = 999,
  className,
}: QuantityStepperProps) {
  return (
    <div className={cn('inline-flex items-center border border-warm-200 rounded-xl', className)}>
      <button
        onClick={() => value > min && onChange(value - 1)}
        disabled={value <= min}
        className="px-3 py-2 text-warm-600 hover:text-ink-black transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
        aria-label="减少数量"
      >
        <Minus className="h-3.5 w-3.5" />
      </button>
      <input
        type="text"
        value={value}
        onChange={(e) => {
          const v = parseInt(e.target.value, 10);
          if (!isNaN(v) && v >= min && v <= max) onChange(v);
        }}
        className="w-12 text-center text-sm font-medium text-ink-black bg-transparent outline-none border-x border-warm-200 py-2"
      />
      <button
        onClick={() => value < max && onChange(value + 1)}
        disabled={value >= max}
        className="px-3 py-2 text-warm-600 hover:text-ink-black transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
        aria-label="增加数量"
      >
        <Plus className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
