import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * 合并 Tailwind CSS 类名，解决冲突
 * 用法：cn('px-4 py-2', 'px-6') → 'px-6 py-2'
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
