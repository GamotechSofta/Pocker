import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createVariantAppConfig } from '../frontend/vite.variant-app.ts'

const appDir = path.dirname(fileURLToPath(import.meta.url))

export default createVariantAppConfig({
  appDir,
  port: 5176,
  variant: 'FIVE_CARD_DRAW',
})
