import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'VITE_')
  return {
    plugins: [
      react(),
      VitePWA({
        registerType: 'autoUpdate',
        // 오프라인 캐시 없음(낡은 화면 방지): precache·runtime 캐시를 모두 끈다.
        workbox: { globPatterns: [], runtimeCaching: [], navigateFallback: null, manifestTransforms: [() => ({ manifest: [], warnings: [] })] },
        manifest: {
          name: '캠공',
          short_name: '캠공',
          display: 'standalone',
          start_url: '/',
          theme_color: '#2563eb',
          background_color: '#ffffff',
          icons: [
            { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
            { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
          ],
        },
      }),
    ],
    server: {
      // 같은 출처를 만들어 SameSite=Lax 세션 쿠키가 동작하게 한다(research 결정 10).
      proxy: { '/api': env.VITE_API_TARGET || 'http://localhost:8080' },
    },
  }
})
