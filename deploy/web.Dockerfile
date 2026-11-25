# 빌드 컨텍스트는 frontend/. 프론트를 빌드해 Caddy 이미지에 dist 를 담는다(Caddyfile 은 compose 가 마운트한다).
# VITE_USE_MOCK 은 넣지 않는다('true' 일 때만 목이 켜진다).
# 빌드 단계는 빌드하는 기계의 플랫폼에서 돈다(정적 파일은 플랫폼과 무관하다).
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build:release

FROM caddy:2
COPY --from=build /app/dist /srv
