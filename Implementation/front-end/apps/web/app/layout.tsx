import type { Metadata, Viewport } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { Providers } from './providers';

const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-geist-sans',
});

export const metadata: Metadata = {
  title: {
    default: '冰美商城 — Technology meets everyday life',
    template: '%s | 冰美商城',
  },
  description:
    '东方自然主义 × 未来生活方式。精选好物，品质之选。科技自然融入生活。',
  manifest: '/manifest.json',
  openGraph: {
    title: '冰美商城',
    description: '东方自然主义 × 未来生活方式。精选好物，品质之选。',
    type: 'website',
    locale: 'zh_CN',
  },
  appleWebApp: {
    capable: true,
    title: '冰美商城',
    statusBarStyle: 'black-translucent',
  },
  robots: {
    index: true,
    follow: true,
  },
  // 性能优化: DNS预解析 + 预连接
  other: {
    'format-detection': 'telephone=no',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  themeColor: '#F7F6F3',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        {/* DNS 预解析 — 加速后端 API 连接 */}
        <link rel="dns-prefetch" href="https://api.icedmall.com" />
        <link rel="preconnect" href="https://api.icedmall.com" crossOrigin="anonymous" />
        {/* 图片 CDN 预连接 */}
        <link rel="dns-prefetch" href="https://img.icedmall.com" />
        <link rel="preconnect" href="https://img.icedmall.com" crossOrigin="anonymous" />
      </head>
      <body className={`${inter.variable} font-sans antialiased bg-mist-white text-ink-black`}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
