import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  output: 'standalone',

  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'img.icedmall.com',
      },
    ],
    formats: ['image/avif', 'image/webp'],
  },
};

export default nextConfig;
