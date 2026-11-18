import { defineConfig } from '@playwright/test'

// 실제 백엔드(local 프로필, :8080)에 붙여 도는 통합 확인. 목은 켜지 않는다.
// 실행: BACKEND_LOG=<백엔드 bootrun.log 경로> [E2E_PORT=5182] npx playwright test -c playwright.real.config.ts
// 포트는 바꿔도 된다: Vite 프록시가 changeOrigin:false라 Host가 브라우저 주소와 같아 Origin 검사(같은 출처)를 통과한다.
// 다른 앱이 그 포트를 쓰고 있으면 붙지 않고 실패하게 reuseExistingServer는 끈다.
export const ORIGIN = `http://localhost:${process.env.E2E_PORT ?? '5173'}`
const port = new URL(ORIGIN).port

export default defineConfig({
  testDir: 'tests/real',
  workers: 1,
  timeout: 120_000,
  webServer: { command: `VITE_USE_MOCK=false npm run dev -- --port ${port} --strictPort`, url: ORIGIN, reuseExistingServer: false },
  use: { baseURL: ORIGIN, extraHTTPHeaders: { Origin: ORIGIN } },
  projects: [
    { name: 'mobile-360', use: { viewport: { width: 360, height: 800 } } },
    { name: 'mobile-430', use: { viewport: { width: 430, height: 900 } } },
    { name: 'desktop-1280', use: { viewport: { width: 1280, height: 800 } } },
  ],
})
