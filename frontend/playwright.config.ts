import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests/e2e',
  webServer: {
    command: 'VITE_USE_MOCK=true npm run dev -- --port 5173',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
  use: { baseURL: 'http://localhost:5173' },
  projects: [
    { name: 'mobile-360', use: { viewport: { width: 360, height: 800 } } },
    { name: 'desktop-1280', use: { viewport: { width: 1280, height: 800 } } },
  ],
})
