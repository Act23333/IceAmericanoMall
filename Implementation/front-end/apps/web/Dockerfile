# ============================================================
# 冰美商城 — 用户端 Next.js 生产镜像
# ============================================================

# ── 阶段1: 依赖安装 ──
FROM node:20-alpine AS deps
RUN corepack enable && corepack prepare pnpm@9 --activate

WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json ./apps/web/
COPY packages/api/package.json ./packages/api/
COPY packages/auth/package.json ./packages/auth/
COPY packages/ui/package.json ./packages/ui/
COPY packages/utils/package.json ./packages/utils/
COPY packages/config/package.json ./packages/config/

RUN pnpm install --frozen-lockfile --prod=false

# ── 阶段2: 构建 ──
FROM deps AS builder
WORKDIR /app
COPY . .

ENV NEXT_PUBLIC_API_URL=http://gateway:8080
RUN pnpm --filter @icedmall/web build

# ── 阶段3: 运行 ──
FROM node:20-alpine AS runner
WORKDIR /app
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000

COPY --from=builder /app/apps/web/.next/standalone ./
COPY --from=builder /app/apps/web/.next/static ./apps/web/.next/static
COPY --from=builder /app/apps/web/public ./apps/web/public

RUN chown -R nextjs:nodejs /app
USER nextjs

EXPOSE 3000
CMD ["node", "apps/web/server.js"]
