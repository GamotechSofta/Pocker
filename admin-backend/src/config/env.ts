import dotenv from 'dotenv';
dotenv.config();

const production = ['production', 'staging'].includes(process.env.NODE_ENV || '');

function configured(name: string, development: string): string {
  const value = process.env[name];
  if (production && (!value || /replace|placeholder|change.me|example/i.test(value))) {
    throw new Error(`${name} must be configured for this environment`);
  }
  return value || development;
}

function secret(name: string, development: string): string {
  const value = configured(name, development);
  if (production && Buffer.byteLength(value) < 32) throw new Error(`${name} must be at least 32 bytes`);
  return value;
}

const allowedOrigins = (process.env.ADMIN_ALLOWED_ORIGINS ?? '').split(',').map(value => value.trim()).filter(Boolean);
for (const value of allowedOrigins) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.origin !== value) throw new Error('ADMIN_ALLOWED_ORIGINS requires exact origins');
}

export const ENV = {
  PRODUCTION: production,
  PORT: process.env.PORT || '5050',
  BIND_ADDRESS: process.env.ADMIN_BIND_ADDRESS || '127.0.0.1',
  MONGODB_URI: configured('MONGODB_URI', 'mongodb://localhost:27019/poker?replicaSet=poker-local'),
  JWT_SECRET: secret('ADMIN_JWT_SECRET', process.env.JWT_SECRET || 'isolated-development-admin-signing-key-only'),
  GAME_SERVICE_URL: process.env.GAME_SERVICE_URL || 'http://localhost:8080',
  ADMIN_API_KEY: secret('ADMIN_API_KEY', ''),
  TOURNAMENT_CLOCK_ADMIN_KEY: secret('TOURNAMENT_CLOCK_ADMIN_KEY', ''),
  ALLOWED_ORIGINS: allowedOrigins,
  TRUST_PROXY: process.env.ADMIN_TRUST_PROXY === '1' ? 1 : false as const,
  BOOTSTRAP_USERNAME: process.env.ADMIN_BOOTSTRAP_USERNAME,
  BOOTSTRAP_PASSWORD: process.env.ADMIN_BOOTSTRAP_PASSWORD,
};

if (production && new Set([ENV.JWT_SECRET, ENV.ADMIN_API_KEY, ENV.TOURNAMENT_CLOCK_ADMIN_KEY]).size !== 3) {
  throw new Error('Administrative signing and service credentials must be distinct');
}
