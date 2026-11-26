import { randomUUID } from 'node:crypto';
import express, { ErrorRequestHandler } from 'express';
import cors from 'cors';
import mongoose from 'mongoose';
import { ENV } from './config/env';
import { connectDB, writableReplica } from './config/database';
import { ensureDefaultAdmin } from './controllers/authController';
import adminRoutes from './routes/adminRoutes';

const app = express();
app.disable('x-powered-by');
app.set('trust proxy', ENV.TRUST_PROXY);
app.use((req, res, next) => {
  const candidate = req.header('X-Request-Id');
  res.setHeader('X-Request-Id', candidate && /^[a-zA-Z0-9_-]{1,64}$/.test(candidate) ? candidate : randomUUID());
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Cache-Control', 'no-store');
  next();
});

app.use(cors({
  origin(origin, done) {
    done(null, Boolean(origin && (ENV.ALLOWED_ORIGINS.includes(origin)
      || (!ENV.PRODUCTION && /^http:\/\/(?:127\.0\.0\.1|localhost)(?::\d+)?$/.test(origin)))));
  },
  allowedHeaders: ['Authorization', 'Content-Type', 'X-Request-Id'],
  exposedHeaders: ['X-Request-Id'],
}));
app.use(express.json({ limit: '128kb' }));
app.get('/health/live', (_req, res) => res.json({ status: 'UP' }));
app.get(['/health', '/health/ready'], async (_req, res) => {
  const ready = await writableReplica().catch(() => false);
  res.status(ready ? 200 : 503).json({ status: ready ? 'UP' : 'DOWN' });
});
app.use('/api/admin', adminRoutes);

const handleError: ErrorRequestHandler = (error, _req, res, next) => {
  if (res.headersSent) return next(error);
  const status = error.type === 'entity.too.large' ? 413 : error.type === 'entity.parse.failed' ? 400 : 500;
  if (status === 500) console.error(JSON.stringify({ event: 'admin_request_failed', requestId: res.getHeader('X-Request-Id') }));
  res.status(status).json({ success: false, message: status === 413 ? 'Request is too large.'
    : status === 400 ? 'Invalid JSON.' : 'Request could not be completed.' });
};
app.use(handleError);

async function bootstrap() {
  await connectDB();
  await ensureDefaultAdmin();
  const port = Number(ENV.PORT);
  const server = app.listen(port, ENV.BIND_ADDRESS, () => {
    console.info(JSON.stringify({ event: 'admin_listening', port }));
  });
  const close = () => {
    setTimeout(() => process.exit(1), 25_000).unref();
    server.close(() => void mongoose.disconnect().then(() => process.exit(0), () => process.exit(1)));
  };
  process.once('SIGTERM', close);
  process.once('SIGINT', close);
}

bootstrap().catch((error: unknown) => {
  console.error(JSON.stringify({
    event: 'admin_bootstrap_failed',
    message: error instanceof Error ? error.message : String(error),
  }));
  process.exit(1);
});
