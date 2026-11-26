import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { AdminUser } from '../models/AdminUser';
import { AuditLog } from '../models/AuditLog';
import { ENV } from '../config/env';
import { AdminAuthRequest } from '../middleware/authMiddleware';

export async function ensureDefaultAdmin(): Promise<void> {
  if (ENV.PRODUCTION) {
    const legacy = await AdminUser.findOne({ username: 'admin', isActive: true });
    if (legacy && await bcrypt.compare('Admin@12345', legacy.passwordHash)) {
      throw new Error('An active legacy development administrator requires credential rotation');
    }
  }
  if (await AdminUser.exists({})) return;
  const username = ENV.BOOTSTRAP_USERNAME?.trim().toLowerCase() || (ENV.PRODUCTION ? '' : 'admin');
  const password = ENV.BOOTSTRAP_PASSWORD || (ENV.PRODUCTION ? '' : 'Admin@12345');
  if (!username || !password || (ENV.PRODUCTION && (Buffer.byteLength(password) < 16 || password === 'Admin@12345'))
      || Buffer.byteLength(password) > 72) throw new Error('Provision the initial administrator with a unique bootstrap credential');
  const passwordHash = await bcrypt.hash(password, 12);
  await AdminUser.updateOne({ username }, { $setOnInsert: { username, passwordHash,
    displayName: 'Super Administrator', role: 'SUPER_ADMIN', isActive: true } }, { upsert: true });
  console.info(JSON.stringify({ event: 'administrator_bootstrapped' }));
}

export async function login(req: Request, res: Response): Promise<void> {
  try {
    const { username, password } = req.body;
    if (typeof username !== 'string' || !username.trim() || username.length > 128 || typeof password !== 'string'
        || !password || Buffer.byteLength(password) > 72) {
      res.status(400).json({ success: false, message: 'Username and password are required' });
      return;
    }

    const user = await AdminUser.findOne({ username: username.trim().toLowerCase() });
    if (!user || !user.isActive) {
      res.status(401).json({ success: false, message: 'Invalid administrative credentials' });
      return;
    }

    const isValid = await bcrypt.compare(password, user.passwordHash);
    if (!isValid) {
      res.status(401).json({ success: false, message: 'Invalid administrative credentials' });
      return;
    }

    user.lastLoginAt = new Date();
    await user.save();

    const token = jwt.sign(
      {},
      ENV.JWT_SECRET,
      { expiresIn: '4h', algorithm: 'HS256', issuer: 'royal-poker-admin', audience: 'royal-poker-operations', subject: user._id.toString() }
    );

    await AuditLog.create({
      adminId: user._id.toString(),
      adminUsername: user.username,
      action: 'ADMIN_LOGIN',
      resource: 'AUTH',
      ipAddress: req.ip,
      timestamp: new Date(),
    });

    res.json({
      success: true,
      token,
      user: {
        id: user._id,
        username: user.username,
        displayName: user.displayName,
        role: user.role,
      },
    });
  } catch (err: any) {
    res.status(500).json({ success: false, message: 'Could not complete administrative sign-in' });
  }
}

export async function getProfile(req: AdminAuthRequest, res: Response): Promise<void> {
  try {
    if (!req.adminUser) {
      res.status(401).json({ success: false, message: 'Unauthorized' });
      return;
    }

    const user = await AdminUser.findById(req.adminUser.id).select('-passwordHash');
    if (!user) {
      res.status(404).json({ success: false, message: 'Admin user not found' });
      return;
    }

    res.json({ success: true, user });
  } catch (err: any) {
    res.status(500).json({ success: false, message: 'Could not load administrative profile' });
  }
}
