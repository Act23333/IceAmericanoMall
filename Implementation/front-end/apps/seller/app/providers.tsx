'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 0, retry: 1, refetchOnWindowFocus: false },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={zhCN}>{children}</ConfigProvider>
    </QueryClientProvider>
  );
}
