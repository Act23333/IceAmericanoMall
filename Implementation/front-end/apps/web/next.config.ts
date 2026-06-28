import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,

  // ── 性能优化 ──

  // 图片优化 — 商品图从后端域名加载
  images: {
    remotePatterns: [
      { protocol: 'https', hostname: 'img.icedmall.com' },
      { protocol: 'https', hostname: 'api.icedmall.com' },
    ],
    formats: ['image/avif', 'image/webp'],
    deviceSizes: [640, 750, 828, 1080, 1200, 1920],
    imageSizes: [16, 32, 48, 64, 96, 128, 256],
  },

  // 压缩 — Brotli 优先 (Nginx 侧配置，此处声明)
  compress: true,

  // 静态资源缓存头
  async headers() {
    return [
      {
        source: '/_next/static/:path*',
        headers: [
          { key: 'Cache-Control', value: 'public, max-age=31536000, immutable' },
        ],
      },
      {
        source: '/icons/:path*',
        headers: [
          { key: 'Cache-Control', value: 'public, max-age=86400' },
        ],
      },
    ];
  },

  // 日志 — 开发阶段保留，生产关闭
  logging: {
    fetches: {
      fullUrl: false,
    },
  },

  // Turbopack (Next.js 15+ 默认)
  experimental: {},
};

export default nextConfig;
