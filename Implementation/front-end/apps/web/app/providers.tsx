'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@icedmall/auth';
import { AUTH_LOGOUT_EVENT } from '@icedmall/api';

function AuthHydrator({ children }: { children: React.ReactNode }) {
  const hydrate = useAuthStore((s) => s.hydrate);
  useEffect(() => { hydrate(); }, [hydrate]);
  return <>{children}</>;
}

/**
 * 全局登出监听 — 刷新令牌失败时（API层广播 auth:logout 事件）
 * 跳转登录页并携带回跳地址，登录成功后自动返回原页面。
 */
function AuthLogoutListener({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const setUser = useAuthStore((s) => s.setUser);

  useEffect(() => {
    const onLogout = () => {
      setUser(null);
      const current = window.location.pathname + window.location.search;
      router.replace(`/auth/login?redirect=${encodeURIComponent(current)}`);
    };
    window.addEventListener(AUTH_LOGOUT_EVENT, onLogout);
    return () => window.removeEventListener(AUTH_LOGOUT_EVENT, onLogout);
  }, [router, setUser]);

  return <>{children}</>;
}

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30 * 1000, retry: 1, refetchOnWindowFocus: false },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <AuthHydrator>
        <AuthLogoutListener>{children}</AuthLogoutListener>
      </AuthHydrator>
    </QueryClientProvider>
  );
}
