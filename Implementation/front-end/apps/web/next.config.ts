import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,

  // API 代理: WSL2 → Windows Gateway
  // 浏览器(Windows)和Next.js服务端(WSL2)都能通过 /api/* 访问后端
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://172.24.176.1:8080/api/:path*',
      },
      {
        source: '/oauth2/:path*',
        destination: 'http://172.24.176.1:8080/oauth2/:path*',
      },
    ];
  },

  // Docker standalone output
  output: 'standalone',

  // 图片优化
  images: {
    remotePatterns: [
      { protocol: 'https', hostname: 'img.icedmall.com' },
      { protocol: 'https', hostname: 'api.icedmall.com' },
      { protocol: 'https', hostname: '**.360buyimg.com' },  // JD CDN
    ],
    formats: ['image/avif', 'image/webp'],
    deviceSizes: [640, 750, 828, 1080, 1200, 1920],
    imageSizes: [16, 32, 48, 64, 96, 128, 256],
  },

  // Brotli 压缩
  compress: true,

  // 静态资源长期缓存
  async headers() {
    return [
      {
        source: '/_next/static/:path*',
        headers: [{ key: 'Cache-Control', value: 'public, max-age=31536000, immutable' }],
      },
      {
        source: '/icons/:path*',
        headers: [{ key: 'Cache-Control', value: 'public, max-age=86400' }],
      },
    ];
  },
};

export default nextConfig;
