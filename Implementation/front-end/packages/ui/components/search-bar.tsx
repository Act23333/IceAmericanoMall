'use client';

import { useState } from 'react';
import { Search, ArrowRight } from 'lucide-react';
import { cn } from '../lib/utils';

interface SearchBarProps {
  className?: string;
  placeholder?: string;
  defaultValue?: string;
  onSearch?: (keyword: string) => void;
}

/**
 * 搜索栏 — 品牌风格「东方自然主义 × 未来玻璃艺术」
 * 毛玻璃底座 + 绿色确认按钮 + 悬停微光
 */
export function SearchBar({
  className,
  placeholder = '搜索商品、品牌、分类…',
  defaultValue,
  onSearch,
}: SearchBarProps) {
  const [value, setValue] = useState(defaultValue ?? '');
  const [focused, setFocused] = useState(false);

  const submit = () => {
    const kw = value.trim();
    if (kw) onSearch?.(kw);
  };

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); submit(); }}
      className={cn('w-full', className)}
    >
      <div
        className={cn(
          'relative flex items-center rounded-2xl border transition-all duration-300',
          'bg-white/60 backdrop-blur-xl shadow-sm',
          focused
            ? 'border-accent-green/40 shadow-lg shadow-accent-green/5 ring-2 ring-accent-green/10'
            : 'border-warm-200 hover:border-warm-400 hover:shadow-md',
        )}
      >
        {/* 搜索图标 */}
        <Search className="absolute left-4 h-4 w-4 text-warm-400 transition-colors duration-300"
          style={{ opacity: focused ? 0.8 : 0.5 }} />

        {/* 输入框 */}
        <input
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          placeholder={placeholder}
          className="flex-1 bg-transparent py-3 pl-11 pr-4 text-sm text-ink-black placeholder:text-warm-400 outline-none"
          onKeyDown={(e) => {
            if (e.key === 'Enter') { e.preventDefault(); submit(); }
          }}
        />

        {/* 确认按钮 — 只在有输入或聚焦时显示 */}
        <div
          className={cn(
            'shrink-0 pr-1.5 transition-all duration-300',
            (value || focused) ? 'opacity-100 translate-x-0' : 'opacity-0 translate-x-2'
          )}
        >
          <button
            type="submit"
            onClick={(e) => { e.preventDefault(); submit(); }}
            className={cn(
              'flex items-center gap-1.5 rounded-xl px-4 py-2 text-sm font-medium',
              'bg-accent-green text-white shadow-sm',
              'transition-all duration-200',
              'hover:bg-accent-green-light hover:shadow-md',
              'active:scale-[0.97]',
              'disabled:opacity-50 disabled:cursor-not-allowed',
            )}
          >
            <span>搜索</span>
            <ArrowRight className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </form>
  );
}
