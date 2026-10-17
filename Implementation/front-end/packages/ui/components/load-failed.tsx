'use client';

import { AlertTriangle, RotateCw } from 'lucide-react';
import { cn } from '../lib/utils';
import { Button } from './button';

interface LoadFailedProps {
  /** 主标题（默认「加载失败」）。 */
  title?: string;
  /** 副文案 —— 必须让用户分辨「加载失败」与「没有数据」，默认已写明。 */
  description?: string;
  /** 重试动作；缺省时只展示状态、不渲染按钮。 */
  onRetry?: () => void;
  /** 重试进行中（按钮置灰 + 文案变化）。 */
  retrying?: boolean;
  /**
   * 紧凑态：页面内某一区块（如评价列表、店铺卡）失败时使用，不撑满屏、不抢主内容。
   * 默认 `false` = 整页失败态。
   */
  compact?: boolean;
  /** 技术细节（错误文案），可选；展示在副文案下方的小字位置。 */
  detail?: string;
  className?: string;
}

/**
 * 加载失败态 —— 与「空态」**必须**视觉与文案可分（设计系统 §4 / 前端规范 §2.3 四态）
 *
 * 为什么单独一个组件：`isError` 命中时若沿用空态渲染，用户看到的是
 * 「暂无数据 / 暂无收藏 / 该分类暂无商品」——把「请求失败」说成「没有数据」，
 * 用户既不知道能重试、也不知道数据其实存在（本仓 B10 前 `isError` 全局 0 命中）。
 *
 * 视觉约定（与空态的差异，改样式时不得退化）：
 *   - 失败态用**语义色 warn/danger** 描边 + `role="alert"`（空态是中性 warm-gray、无 role）
 *   - 失败态**必带重试按钮**（空态给的是「去逛逛」这类导航 CTA）
 *   - 失败态文案明确写「加载失败 / 网络或服务异常」，不写「暂无」
 *
 * 本组件是**纯展示**（无路由、无数据获取依赖）—— 重试动作由调用方注入，
 * 因此 Web / 桌面端 / 其他 Next 应用可各自接 `router.refresh()` 或 react-query `refetch`。
 */
export function LoadFailed({
  title = '加载失败',
  description = '网络或服务暂时不可用，请重试。',
  onRetry,
  retrying = false,
  compact = false,
  detail,
  className,
}: LoadFailedProps) {
  return (
    <div
      role="alert"
      aria-live="polite"
      className={cn(
        'rounded-2xl border border-danger/25 bg-danger/5 text-center',
        compact ? 'px-4 py-6' : 'px-6 py-16',
        className,
      )}
    >
      <AlertTriangle
        className={cn('mx-auto text-danger/70', compact ? 'h-6 w-6' : 'h-10 w-10')}
        aria-hidden="true"
      />
      <p className={cn('mt-3 font-medium text-ink-black', compact ? 'text-sm' : 'text-base')}>
        {title}
      </p>
      {description && (
        <p className="mt-1.5 text-xs text-text-secondary">{description}</p>
      )}
      {detail && (
        <p className="mt-1.5 text-xs text-text-tertiary break-words">{detail}</p>
      )}
      {onRetry && (
        <Button
          onClick={onRetry}
          variant="secondary"
          size={compact ? 'sm' : 'md'}
          loading={retrying}
          className="mt-4"
        >
          {!retrying && <RotateCw className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />}
          {retrying ? '重试中…' : '重试'}
        </Button>
      )}
    </div>
  );
}
