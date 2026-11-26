import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // Keep Vite's generated config bundle outside node_modules so restricted or
  // read-only dependency trees do not block production builds.
  cacheDir: '.vite-cache',
  build: {
    outDir: '.build/dist',
  },
  server: {
    port: 5180,
    host: true,
  },
});
