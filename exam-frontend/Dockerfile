# ── Build stage: Node 22 ──
FROM node:22-alpine AS build
WORKDIR /build

# npm dependencies (Docker layer cache)
COPY package.json package-lock.json ./
RUN npm ci

# Source + build (vite build 自动读取 .env.production)
COPY . .
RUN npm run build

# ── Serve stage: nginx ──
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# 前端静态文件
COPY --from=build /build/dist ./

# nginx 配置（覆盖默认）
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
