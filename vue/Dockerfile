# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
# vite 在 devDependencies：显式声明 NODE_ENV，并用 --include=dev 双保险
RUN NODE_ENV=development npm ci --include=dev --no-audit --no-fund
COPY . .

# 构建期自检：vite 没装上就立刻失败（并打印诊断信息）
RUN test -x node_modules/.bin/vite \
    || (echo "=== npm ls ==="; npm ls --depth=0; exit 1)

# 2G 机器构建时防止 Node 内存溢出
ENV NODE_OPTIONS=--max-old-space-size=1024
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80