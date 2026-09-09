'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Search, ShoppingBag, User, Menu, X } from 'lucide-react';
import { cn } from '@icedmall/ui';
import { useAuthStore } from '@icedmall/auth';

/** 轮播热点关键词 */
const HOT_KEYWORDS = ['手工陶瓷', '降噪耳机', '明前龙井', '亚麻围巾', '瑜伽垫', '原木台灯'];

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const [hotIdx, setHotIdx] = useState(0);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const user = useAuthStore((s) => s.user);
  const router = useRouter();

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 40);
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // 热点关键词轮播（每 3 秒切换）
  useEffect(() => {
    if (!searchOpen) return;
    const t = setInterval(() => setHotIdx((i) => (i + 1) % HOT_KEYWORDS.length), 3000);
    return () => clearInterval(t);
  }, [searchOpen]);

  // 展开时聚焦输入框
  useEffect(() => {
    if (searchOpen) searchInputRef.current?.focus();
  }, [searchOpen]);

  const submitSearch = (kw?: string) => {
    const keyword = (kw ?? searchValue).trim();
    if (!keyword) return;
    setSearchOpen(false);
    setSearchValue('');
    router.push(`/search?keyword=${encodeURIComponent(keyword)}`);
  };

  return (
    <header
      className={cn(
        'fixed top-0 left-0 right-0 z-50 transition-all duration-300',
        scrolled
          ? 'bg-white/80 backdrop-blur-xl border-b border-warm-200 shadow-sm'
          : 'bg-transparent',
      )}
    >
      <nav className="mx-auto max-w-7xl px-4 h-16 flex items-center justify-between gap-3">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 text-ink-black font-medium text-lg select-none shrink-0">
          <span className="text-accent-green">◆</span>
          冰美
        </Link>

        {/* 桌面导航链接 */}
        <div className="hidden md:flex items-center gap-8 shrink-0">
          <Link href="/marketplace" className="text-sm text-ink-soft hover:text-ink-black transition-colors">
            商城
          </Link>
          <Link href="/marketplace?sort=newest" className="text-sm text-ink-soft hover:text-ink-black transition-colors">
            新品
          </Link>
        </div>

        {/* 右侧操作区 */}
        <div className="flex items-center gap-2 min-w-0 flex-1 justify-end">
          {/* 搜索 — 收缩图标 / 点击丝滑展开 */}
          <div
            className={cn(
              'flex items-center rounded-full border transition-all duration-500 ease-out',
              searchOpen
                ? 'w-64 md:w-80 border-accent/40 bg-white/90 backdrop-blur-xl shadow-lg px-3'
                : 'w-10 border-transparent bg-transparent',
            )}
          >
            {searchOpen ? (
              <div className="flex items-center w-full">
                <input
                  ref={searchInputRef}
                  type="text"
                  value={searchValue}
                  onChange={(e) => setSearchValue(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') { e.preventDefault(); submitSearch(); }
                    if (e.key === 'Escape') { setSearchOpen(false); setSearchValue(''); }
                  }}
                  placeholder={HOT_KEYWORDS[hotIdx]}
                  className="flex-1 bg-transparent py-2 text-sm text-ink-black placeholder:text-warm-400 outline-none"
                />
                <button
                  onClick={() => submitSearch()}
                  className="shrink-0 text-accent-green hover:text-accent-green-dark transition-colors p-1"
                  aria-label="搜索"
                >
                  <Search className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <button
                onClick={() => setSearchOpen(true)}
                className="w-full h-full flex items-center justify-center text-ink-soft hover:text-ink-black transition-colors"
                aria-label="展开搜索"
              >
                <Search className="h-5 w-5" />
              </button>
            )}
          </div>

          {/* 购物车 */}
          <Link href="/shop/cart"
            className="p-2 text-ink-soft hover:text-ink-black transition-colors rounded-full hover:bg-warm-100 relative shrink-0"
            aria-label="购物车">
            <ShoppingBag className="h-5 w-5" />
          </Link>

          {/* 用户 */}
          {user ? (
            <Link href="/user/profile"
              className="p-2 text-ink-soft hover:text-ink-black transition-colors rounded-full hover:bg-warm-100 shrink-0"
              aria-label="个人中心">
              <User className="h-5 w-5" />
            </Link>
          ) : (
            <Link href="/auth/login"
              className="ml-1 rounded-xl bg-accent-green px-4 py-1.5 text-sm font-medium text-white transition-all hover:bg-accent-green-light active:scale-[0.98] shrink-0">
              登录
            </Link>
          )}

          {/* 移动端菜单按钮 */}
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="md:hidden p-2 text-ink-soft hover:text-ink-black transition-colors rounded-full hover:bg-warm-100 shrink-0"
            aria-label={mobileMenuOpen ? '关闭菜单' : '打开菜单'}
          >
            {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </nav>

      {/* 移动端抽屉菜单 */}
      {mobileMenuOpen && (
        <div className="md:hidden border-t border-warm-200 bg-white/95 backdrop-blur-xl animate-fade-up">
          <div className="px-4 py-4 space-y-3">
            <Link href="/marketplace" onClick={() => setMobileMenuOpen(false)}
              className="block py-2 text-sm text-ink-black">商城</Link>
            <Link href="/shop/cart" onClick={() => setMobileMenuOpen(false)}
              className="block py-2 text-sm text-ink-black">购物车</Link>
            {!user && (
              <Link href="/auth/login" onClick={() => setMobileMenuOpen(false)}
                className="block py-2 text-sm text-accent-green font-medium">登录 / 注册</Link>
            )}
          </div>
        </div>
      )}

      {/* 点击外部关闭搜索 */}
      {searchOpen && (
        <div className="fixed inset-0 -z-10" onClick={() => { setSearchOpen(false); setSearchValue(''); }} />
      )}
    </header>
  );
}
