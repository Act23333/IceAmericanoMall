'use client';

import { useState, useRef } from 'react';
import { Search } from 'lucide-react';
import { cn } from '../lib/utils';

interface SearchBarProps {
  className?: string;
  placeholder?: string;
  defaultValue?: string;
  onSearch?: (keyword: string) => void;
}

export function SearchBar({ className, placeholder = '搜索商品、品牌、分类…', defaultValue, onSearch }: SearchBarProps) {
  const [value, setValue] = useState(defaultValue ?? '');
  const inputRef = useRef<HTMLInputElement>(null);

  const submit = () => {
    const kw = value.trim();
    if (kw) {
      onSearch?.(kw);
      // 保持关键词在输入框
      setValue(kw);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    submit();
  };

  return (
    <form onSubmit={handleSubmit} className={cn('w-full', className)}>
      <div className="relative flex items-center">
        <Search className="absolute left-3.5 h-4 w-4 text-warm-400 pointer-events-none" />
        <input
          ref={inputRef}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={placeholder}
          className="w-full rounded-l-full border border-r-0 border-warm-200 bg-white/70 py-2.5 pl-10 pr-4 text-sm text-ink-black placeholder:text-warm-400 outline-none transition-all backdrop-blur-sm focus:border-accent-green focus:bg-white focus:ring-2 focus:ring-accent-green/20"
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              submit();
            }
          }}
        />
        <button
          type="submit"
          onClick={(e) => { e.preventDefault(); submit(); }}
          className="shrink-0 rounded-r-full bg-accent-green px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-green-light active:bg-accent-green-dark"
        >
          搜索
        </button>
      </div>
    </form>
  );
}
