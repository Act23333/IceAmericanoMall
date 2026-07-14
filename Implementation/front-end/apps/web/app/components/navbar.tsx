'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Search, ShoppingBag, User, Menu, X } from 'lucide-react';
import { cn } from '@icedmall/ui';
import { useAuthStore } from '@icedmall/auth';
import { getAccessToken } from '@icedmall/api';

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const storeUser = useAuthStore((s) => s.user);
  // hydrate 失败时，token cookie 存在就认为已登录（降级展示）
  const hasToken = typeof window !== 'undefined' && !!getAccessToken();
  const user = storeUser || (hasToken ? { userId: '', username: '' } : null);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 40);
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header
      className={cn(
        'fixed top-0 left-0 right-0 z-50 transition-all duration-300',
        scrolled
          ? 'bg-white/80 backdrop-blur-xl border-b border-warm-200 shadow-sm'
          : 'bg-transparent',
      )}
    >
      <nav className="mx-auto max-w-7xl px-4 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 text-ink-black font-medium text-lg select-none">
          <span className="text-accent-green">◆</span>
          冰美
        </Link>

        {/* 桌面导航链接 */}
        <div className="hidden md:flex items-center gap-8">
          <Link href="/marketplace" className="text-sm text-ink-soft hover:text-ink-black transition-colors">
            商城
          </Link>
          <Link href="/marketplace?sort=newest" className="text-sm text-ink-soft hover:text-ink-black transition-colors">
            新品
          </Link>
        </div>

        {/* 右侧操作区 */}
        <div className="flex items-center gap-2">
          {/* 搜索 */}
          <Link href="/search"
            className="p-2 text-ink-soft hover:text-ink-black transition-colors rounded-full hover:bg-warm-100"
            aria-label="搜索">
            <Search className="h-5 w-5" />
          </Link>

          {/* 购物车 */}
          <Link href="/shop/cart"
            className="p-2 text-ink-soft hover:text-ink-black transition-colors rounded-full hover:bg-warm-100 relative"
            aria-label="购物车">
            <ShoppingBag className="h-5 w-5" />
          </Link>

          {/* 用户 */}
          {user ? (
            <Link href="/user/profile"
              className="p-2 text-ink-soft hover:text-ink-black transition-colors rounded-full hover:bg-warm-100"
              aria-label="个人中心">
              <User className="h-5 w-5" />
            </Link>
          ) : (
            <Link href="/auth/login"
              className="ml-2 rounded-xl bg-accent-green px-4 py-1.5 text-sm font-medium text-white transition-all hover:bg-accent-green-light active:scale-[0.98]">
              登录
            </Link>
          )}

          {/* 移动端菜单按钮 */}
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="md:hidden p-2 text-ink-soft hover:text-ink-black transition-colors rounded-full hover:bg-warm-100"
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
    </header>
  );
}
