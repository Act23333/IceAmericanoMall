'use client';

import { useEffect, useRef, useState } from 'react';
import { Search, ArrowRight, Clock, Flame, X } from 'lucide-react';
import { cn } from '../lib/utils';

interface SearchBarProps {
  className?: string;
  placeholder?: string;
  defaultValue?: string;
  hotKeywords?: string[];
  history?: string[];
  onSearch?: (keyword: string) => void;
  onClearHistory?: () => void;
}

/**
 * 搜索栏 — 品牌风格「东方自然主义 × 未来玻璃艺术」
 *
 * 特性:
 *   1. 未聚焦时 placeholder 显示轮播热点关键词（每3秒切换）
 *   2. 点击关键词可覆盖输入内容
 *   3. 聚焦展开下拉面板: 搜索历史 + 热门搜索
 *   4. 回车或点击搜索按钮提交
 */
export function SearchBar({
  className,
  placeholder = '搜索商品、品牌、分类…',
  defaultValue,
  hotKeywords = ['手工陶瓷', '降噪耳机', '明前龙井', '亚麻围巾', '瑜伽垫', '原木台灯'],
  history = [],
  onSearch,
  onClearHistory,
}: SearchBarProps) {
  const [value, setValue] = useState(defaultValue ?? '');
  const [focused, setFocused] = useState(false);
  const [hotIdx, setHotIdx] = useState(0);
  const [showPanel, setShowPanel] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  // 轮播热点关键词
  useEffect(() => {
    const t = setInterval(() => setHotIdx((i) => (i + 1) % hotKeywords.length), 3000);
    return () => clearInterval(t);
  }, [hotKeywords.length]);

  // 点击外部关闭面板
  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)
          && inputRef.current && !inputRef.current.contains(e.target as Node)) {
        setShowPanel(false);
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const submit = (kw?: string) => {
    const keyword = (kw ?? value).trim();
    if (!keyword) return;
    setShowPanel(false);
    setValue('');
    onSearch?.(keyword);
  };

  return (
    <div ref={panelRef} className={cn('relative w-full', className)}>
      <form
        onSubmit={(e) => { e.preventDefault(); submit(); }}
      >
        <div
          className={cn(
            'relative flex items-center rounded-2xl border transition-all duration-300',
            'bg-white/60 backdrop-blur-xl shadow-sm',
            focused
              ? 'border-accent/40 shadow-lg ring-2 ring-accent/10'
              : 'border-warm-gray-200 hover:border-warm-gray-400 hover:shadow-md',
          )}
        >
          {/* 搜索图标 */}
          <Search className="absolute left-4 h-4 w-4 text-text-tertiary transition-colors duration-300"
            style={{ opacity: focused ? 0.8 : 0.5 }} />

          {/* 输入框 */}
          <input
            ref={inputRef}
            type="text"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onFocus={() => { setFocused(true); setShowPanel(true); }}
            placeholder={value ? undefined : (focused ? placeholder : `🔍 ${hotKeywords[hotIdx]}`)}
            className="flex-1 bg-transparent py-3 pl-11 pr-24 text-sm text-ink-black placeholder:text-text-tertiary outline-none transition-all"
            onKeyDown={(e) => {
              if (e.key === 'Enter') { e.preventDefault(); submit(); }
            }}
          />

          {/* 清除按钮 */}
          {value && (
            <button
              type="button"
              onClick={() => setValue('')}
              className="absolute right-20 p-1 text-text-tertiary hover:text-ink-black transition-colors"
              aria-label="清除"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}

          {/* 确认按钮 */}
          <div
            className={cn(
              'shrink-0 pr-1.5 transition-all duration-300',
              (value || focused) ? 'opacity-100 translate-x-0' : 'opacity-0 translate-x-2 pointer-events-none',
            )}
          >
            <button
              type="submit"
              className={cn(
                'flex items-center gap-1.5 rounded-xl px-4 py-2 text-sm font-medium',
                'bg-accent text-white shadow-sm',
                'transition-all duration-200',
                'hover:bg-accent-hover hover:shadow-md',
                'active:scale-[0.97]',
              )}
            >
              <span>搜索</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </form>

      {/* 下拉面板 */}
      {showPanel && (
        <div className="absolute top-full left-0 right-0 mt-2 rounded-2xl bg-white/90 backdrop-blur-xl border border-white/40 shadow-glass-lg p-4 z-50 animate-scale-in">
          {/* 搜索历史 */}
          {history.length > 0 && (
            <div className="mb-3">
              <div className="flex items-center justify-between mb-2">
                <span className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                  <Clock className="h-3 w-3" /> 搜索历史
                </span>
                {onClearHistory && (
                  <button onClick={onClearHistory} className="text-xs text-text-tertiary hover:text-danger transition-colors">
                    清空
                  </button>
                )}
              </div>
              <div className="flex flex-wrap gap-2">
                {history.map((kw, i) => (
                  <button
                    key={i}
                    onClick={() => submit(kw)}
                    className="px-3 py-1.5 text-xs rounded-full bg-warm-gray-100 text-text-secondary hover:bg-accent/10 hover:text-accent transition-colors"
                  >
                    {kw}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* 热门搜索 */}
          <div>
            <span className="flex items-center gap-1.5 text-xs font-medium text-text-secondary mb-2">
              <Flame className="h-3 w-3" /> 热门搜索
            </span>
            <div className="flex flex-wrap gap-2">
              {hotKeywords.map((kw, i) => (
                <button
                  key={i}
                  onClick={() => submit(kw)}
                  className="px-3 py-1.5 text-xs rounded-full border border-warm-gray-200 text-text-secondary hover:border-accent/40 hover:text-accent transition-colors"
                >
                  {kw}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
