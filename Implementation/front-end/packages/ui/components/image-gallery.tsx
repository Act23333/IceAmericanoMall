'use client';

import { useState } from 'react';
import { cn } from '../lib/utils';

interface ImageGalleryProps {
  images: string[];
  alt: string;
  className?: string;
}

/**
 * 商品图片画廊 — Client Component
 *
 * 左侧缩略图列表 + 右侧主图
 * GPU-friendly: hover zoom 仅用 transform scale
 */
export function ImageGallery({ images, alt, className }: ImageGalleryProps) {
  const displayImages = images.length > 0 ? images : [''];
  const [activeIndex, setActiveIndex] = useState(0);

  return (
    <div className={cn('flex gap-4', className)}>
      {/* 缩略图列 */}
      {displayImages.length > 1 && (
        <div className="hidden md:flex flex-col gap-2 w-20 shrink-0">
          {displayImages.map((img, i) => (
            <button
              key={i}
              onClick={() => setActiveIndex(i)}
              className={cn(
                'aspect-square w-full overflow-hidden rounded-lg border-2 transition-all',
                i === activeIndex
                  ? 'border-accent-gold'
                  : 'border-transparent hover:border-warm-200',
              )}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={img}
                alt={`${alt} - ${i + 1}`}
                className="h-full w-full object-cover"
                loading="lazy"
              />
            </button>
          ))}
        </div>
      )}

      {/* 主图 */}
      <div className="flex-1 overflow-hidden rounded-2xl bg-warm-100 aspect-square">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={displayImages[activeIndex]}
          alt={alt}
          className="h-full w-full object-cover transition-transform duration-500 hover:scale-105"
        />
      </div>
    </div>
  );
}
