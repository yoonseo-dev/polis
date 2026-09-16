import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// polis-server(Spring Boot)는 8080에서 돈다. CORS를 백엔드에 새로 얹는 대신
// dev 서버가 /api, /ws를 그대로 8080으로 중계한다 — M2-4까지 검증된 서버 코드는 손대지 않는다.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
      },
      '/ws': {
        target: 'ws://localhost:8090',
        ws: true,
      },
    },
  },
})
