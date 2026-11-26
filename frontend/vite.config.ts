import path from 'node:path'
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type UserConfig } from 'vite'

const rootDir = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig(({ mode }) => {
  const { POKER_DEV_BACKEND_URL = 'http://localhost:8080' } = loadEnv(mode, rootDir, 'POKER_DEV_')

  return {
    plugins: [react(), tailwindcss()],
    server: {
      host: true,
      // Keep the optional all-games hub away from the Hold'em frontend.
      port: 5170,
      strictPort: true,
      proxy: {
        '/api': {
          target: POKER_DEV_BACKEND_URL,
          changeOrigin: true,
        },
        '/ws': {
          target: POKER_DEV_BACKEND_URL,
          ws: true,
        },
      },
    },
    resolve: {
      alias: {
        '@': path.resolve(rootDir, 'src'),
      },
    },
    build: {
      rolldownOptions: {
        output: {
          codeSplitting: {
            groups: [{ name: 'react-vendor', test: /node_modules[\\/](?:react(?:-dom|-router(?:-dom)?)?|scheduler)[\\/]/ }],
          },
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      env: {
        VITE_API_URL: '',
        VITE_WS_URL: 'ws://localhost:8080',
      },
    },
  } as UserConfig
})
