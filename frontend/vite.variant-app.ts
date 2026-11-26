import path from 'node:path'
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type UserConfig } from 'vite'

const sharedDir = fileURLToPath(new URL('.', import.meta.url))

export type VariantAppOptions = {
  /** Absolute path to the app folder (apps/texas-holdem). */
  appDir: string
  port: number
  variant: string
}

/** Shared Vite config for the five single-variant React apps. */
export function createVariantAppConfig({ appDir, port, variant }: VariantAppOptions) {
  return defineConfig(({ mode }) => {
    const env = loadEnv(mode, appDir, '')
    const backend = env.POKER_DEV_BACKEND_URL || 'http://localhost:8080'

    return {
      root: appDir,
      envDir: appDir,
      // Keep each standalone frontend locked even when Vite is started from
      // another working directory or through npm --prefix.
      define: {
        __POKER_VARIANT__: JSON.stringify(variant),
      },
      publicDir: path.resolve(sharedDir, 'public'),
      plugins: [react(), tailwindcss()],
      server: {
        host: true,
        port,
        strictPort: true,
        fs: {
          allow: [sharedDir, appDir, path.resolve(sharedDir, '..')],
        },
        proxy: {
          '/api': { target: backend, changeOrigin: true },
          '/ws': { target: backend, ws: true },
        },
      },
      preview: {
        host: true,
        port,
        strictPort: true,
      },
      resolve: {
        alias: {
          '@': path.resolve(sharedDir, 'src'),
          // Variant entry points live outside frontend/src, so resolve React
          // from the shared frontend install instead of the app directory.
          react: path.resolve(sharedDir, 'node_modules/react'),
          'react-dom': path.resolve(sharedDir, 'node_modules/react-dom'),
        },
      },
      build: {
        outDir: path.resolve(appDir, 'dist'),
        emptyOutDir: true,
      },
    } as UserConfig
  })
}
