import type { Config } from 'tailwindcss';

/**
 * 共享 Tailwind CSS 配置
 * 冰美商城「东方自然主义 × 未来玻璃艺术」色板
 *
 * 每个 app 的 tailwind.config.ts 通过 presets 继承此配置
 */

const config: Config = {
  theme: {
    extend: {
      colors: {
        // 基础色 — 替代原来的 blue primary
        mist: {
          DEFAULT: '#F7F6F3',
          50: '#FAFAF8',
          100: '#F7F6F3',
          200: '#F0EFEC',
          300: '#E8E6E1',
        },
        warm: {
          DEFAULT: '#F0EFEC',
          50: '#FAFAF8',
          100: '#F0EFEC',
          200: '#E8E6E1',
          400: '#B8B5B0',
          600: '#8C8A87',
        },
        ink: {
          DEFAULT: '#1A1A1A',
          soft: '#2D2D2D',
        },

        // 点缀色
        accent: {
          green: '#4A7C59',
          'green-light': '#6B9E7A',
          'green-dark': '#3A6346',
          gold: '#C4A747',
          'gold-light': '#D9C56E',
          purple: '#6B7DB3',
          'purple-dark': '#4A5A8A',
        },

        // 保留 primary/secondary 向后兼容
        primary: {
          DEFAULT: '#4A7C59',
          light: '#6B9E7A',
          dark: '#3A6346',
          50: '#F0F5F1',
          100: '#E0EBE4',
          200: '#C1D7C8',
          300: '#A3C3AD',
          400: '#84AF91',
          500: '#6B9E7A',
          600: '#4A7C59',
          700: '#3A6346',
          800: '#2C4A34',
          900: '#1D3122',
        },
        secondary: {
          DEFAULT: '#C4A747',
          50: '#FBF9F2',
          100: '#F7F2E0',
          500: '#C4A747',
          700: '#A68E2E',
        },

        success: '#4A7C59',
        danger: '#C44E4E',
        warning: '#C4A747',
        info: '#6B7DB3',
      },
      fontFamily: {
        sans: ['var(--font-geist-sans)', 'system-ui', 'sans-serif'],
        mono: ['var(--font-geist-mono)', 'monospace'],
      },
      boxShadow: {
        glass: '0 8px 32px rgba(0, 0, 0, 0.04)',
        'glass-lg': '0 16px 48px rgba(0, 0, 0, 0.06)',
      },
      backdropBlur: {
        glass: '20px',
      },
      animation: {
        'fade-in': 'fadeIn 0.4s ease-out',
        'fade-up': 'fadeUp 0.6s cubic-bezier(0.4, 0, 0.2, 1)',
        'slide-up': 'slideUp 0.3s ease-out',
        'scale-in': 'scaleIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)',
        'glass-shimmer': 'shimmer 3s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        fadeUp: {
          '0%': { opacity: '0', transform: 'translateY(24px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        scaleIn: {
          '0%': { opacity: '0', transform: 'scale(0.97)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        shimmer: {
          '0%, 100%': { opacity: '0.6' },
          '50%': { opacity: '1' },
        },
      },
    },
  },
  plugins: [],
};

export default config;
