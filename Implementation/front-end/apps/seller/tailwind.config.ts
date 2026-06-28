import type { Config } from 'tailwindcss';
import tailwindConfig from '@icedmall/config/tailwind.config';

const config: Config = {
  presets: [tailwindConfig],
  content: [
    './app/**/*.{ts,tsx}',
    '../../packages/ui/components/**/*.{ts,tsx}',
  ],
};

export default config;
