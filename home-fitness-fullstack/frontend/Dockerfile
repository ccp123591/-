# ---- build stage ----
FROM node:20-alpine AS build
WORKDIR /src
# 中国大陆 npm 镜像加速
RUN npm config set registry https://registry.npmmirror.com
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- serve stage ----
FROM nginx:alpine
COPY --from=build /src/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
