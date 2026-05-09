# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --ignore-scripts

COPY tsconfig.json ./
COPY src ./src

RUN npm run build

# ── Stage 2: Production ────────────────────────────────────────────────────────
FROM node:20-alpine AS production

WORKDIR /app

ENV NODE_ENV=production

# Install only production deps
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev --ignore-scripts

# Copy compiled output
COPY --from=builder /app/dist ./dist

# Create Valhalla live traffic directory (can be overridden via volume mount)
RUN mkdir -p /data/valhalla/live_traffic

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:3000/health || exit 1

CMD ["node", "dist/index.js"]
