FROM node:20-alpine AS deps
RUN corepack enable && corepack prepare pnpm@9 --activate

WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/admin/package.json ./apps/admin/
COPY packages/api/package.json ./packages/api/
COPY packages/auth/package.json ./packages/auth/
COPY packages/ui/package.json ./packages/ui/
COPY packages/utils/package.json ./packages/utils/
COPY packages/config/package.json ./packages/config/

RUN pnpm install --frozen-lockfile --prod=false

FROM deps AS builder
WORKDIR /app
COPY . .

ENV NEXT_PUBLIC_API_URL=http://gateway:8080
RUN pnpm --filter @icedmall/admin build

FROM node:20-alpine AS runner
WORKDIR /app
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3001

COPY --from=builder /app/apps/admin/.next/standalone ./
COPY --from=builder /app/apps/admin/.next/static ./apps/admin/.next/static
COPY --from=builder /app/apps/admin/public ./apps/admin/public

RUN chown -R nextjs:nodejs /app
USER nextjs

EXPOSE 3001
CMD ["node", "apps/admin/server.js"]
