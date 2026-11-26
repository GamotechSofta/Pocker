import mongoose from 'mongoose';
import { ENV } from './env';

export async function connectDB(): Promise<void> {
  await mongoose.connect(ENV.MONGODB_URI, {
    serverSelectionTimeoutMS: 5000,
    maxPoolSize: 30,
    autoIndex: !ENV.PRODUCTION || process.env.MONGODB_AUTO_INDEX_CREATION === 'true',
  });
  if (!await writableReplica()) throw new Error('MongoDB replica-set primary is required');
  console.info(JSON.stringify({ event: 'admin_database_connected' }));
}

export async function writableReplica(): Promise<boolean> {
  if (!mongoose.connection.db || mongoose.connection.readyState !== 1) return false;
  try {
    const hello = await mongoose.connection.db.command({ hello: 1 });
    // Local development may use a standalone MongoDB. Production still
    // requires a writable replica-set primary for transactional behavior.
    if (!ENV.PRODUCTION && !hello.setName) return hello.ok === 1;
    return Boolean(hello.setName && hello.isWritablePrimary && typeof hello.logicalSessionTimeoutMinutes === 'number');
  } catch {
    return false;
  }
}
