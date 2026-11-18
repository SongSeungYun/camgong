# 빌드 컨텍스트는 frontend/. 프론트를 빌드해 Caddy 이미지에 dist 를 담는다(Caddyfile 은 compose 가 마운트한다).
# VITE_USE_MOCK 은 넣지 않는다('true' 일 때만 목이 켜진다).
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM caddy:2
COPY --from=build /app/dist /srv
