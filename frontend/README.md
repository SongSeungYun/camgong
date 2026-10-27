# 캠공 프론트엔드

모바일 우선 웹(PWA). 명세: `specs/008-frontend-web/`.

## 실행

```sh
npm install
npm run dev                         # 실제 백엔드: /api → http://localhost:8080
VITE_API_TARGET=http://host:8080 npm run dev   # 백엔드 주소 변경
VITE_USE_MOCK=true npm run dev      # 백엔드 없이 MSW 목으로 실행
```

## 테스트

```sh
npm test            # Vitest (MSW 목)
npm run e2e         # Playwright (360px 모바일 + 1280px 데스크톱, 목 모드)
npm run typecheck
npm run lint
```

환경 값은 `VITE_USE_MOCK`, `VITE_API_TARGET` 둘뿐이다. 시크릿은 두지 않는다.

## 결정 기록 (2026-10-27, 사용자)

- PWA precache: 아이콘 2개와 `manifest.webmanifest`가 precache에 남는 상태를 그대로 둔다(화면 HTML/JS는 캐시하지 않음).
- `typescript-eslint`·`@eslint/js`는 추가하지 않는다. TS 검사는 `tsc` strict(`npm run typecheck`)로 하고, `lint`는 현 상태(.js만)를 유지한다.
