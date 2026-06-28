'use client';

import { useState } from 'react';
import { Search } from 'lucide-react';
import { cn } from '../lib/utils';

interface SearchBarProps {
  className?: string;
  placeholder?: string;
  onSearch?: (keyword: string) => void;
}

/**
 * 搜索栏 — 通用组件
 *
 * 品牌风格：毛玻璃背景 + 绿色聚焦环
 */
export function SearchBar({
  className,
  placeholder = '搜索商品、品牌、分类…',
  onSearch,
}: SearchBarProps) {
  const [value, setValue] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (value.trim()) {
      onSearch?.(value.trim());
    }
  };

  return (
    <form onSubmit={handleSubmit} className={cn('w-full', className)}>
      <div className="relative">
        <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-warm-400" />
        <input
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={placeholder}
          className="w-full rounded-full border border-warm-200 bg-white/70 py-2.5 pl-10 pr-4 text-sm text-ink-black placeholder:text-warm-400 outline-none transition-all backdrop-blur-sm focus:border-accent-green focus:bg-white focus:ring-2 focus:ring-accent-green/20 hover:border-warm-400"
        />
      </div>
    </form>
  );
}
