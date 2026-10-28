import { defineConfig } from '@playwright/test'

// 실제 백엔드(local 프로필, :8080)에 붙여 도는 통합 확인. 목은 켜지 않는다.
// 실행: BACKEND_LOG=<백엔드 bootrun.log 경로> npx playwright test -c playwright.real.config.ts
export default defineConfig({
  testDir: 'tests/real',
  workers: 1,
  timeout: 120_000,
  webServer: { command: 'VITE_USE_MOCK=false npm run dev -- --port 5173 --strictPort', url: 'http://localhost:5173', reuseExistingServer: true },
  use: { baseURL: 'http://localhost:5173', extraHTTPHeaders: { Origin: 'http://localhost:5173' } },
  projects: [
    { name: 'mobile-360', use: { viewport: { width: 360, height: 800 } } },
    { name: 'mobile-430', use: { viewport: { width: 430, height: 900 } } },
  ],
})
