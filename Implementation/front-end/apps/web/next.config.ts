import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,

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
