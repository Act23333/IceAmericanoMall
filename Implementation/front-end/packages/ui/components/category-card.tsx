import { cn } from '../lib/utils';
import type { CategoryVO } from '@icedmall/api';

interface CategoryCardProps {
  category: CategoryVO;
  className?: string;
}

const ICON_MAP: Record<string, string> = {
  default: '📦',
};

/**
 * 分类入口卡片
 *
 * 品牌风格：玻璃质感 + 图标 + 简约文字
 * hover: scale + 微光
 */
export function CategoryCard({ category, className }: CategoryCardProps) {
  const icon = ICON_MAP[category.name] || ICON_MAP.default;

  return (
    <a
      href={`/marketplace?categoryId=${category.id}`}
      className={cn(
        'group flex flex-col items-center gap-3 rounded-2xl p-6 transition-all duration-300',
        'border border-warm-200 bg-white/70 backdrop-blur-sm',
        'hover:scale-[1.03] hover:shadow-glass hover:border-accent-green/20',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-green/40',
        className,
      )}
    >
      {/* 图标占位 — 后期替换为真实图片 */}
      <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-gradient-to-br from-accent-green/10 to-accent-blue-purple/5 text-2xl">
        {icon}
      </div>
      <div className="text-center">
        <h4 className="text-sm font-medium text-ink-black">{category.name}</h4>
        {category.children && category.children.length > 0 && (
          <p className="mt-1 text-xs text-warm-400">
            {category.children.length} 个子类目
          </p>
        )}
      </div>
    </a>
  );
}
