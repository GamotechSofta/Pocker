import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { ENV } from '../config/env';
import { AdminUser } from '../models/AdminUser';

export interface AdminAuthRequest extends Request {
  adminUser?: {
    id: string;
    username: string;
    role: string;
  };
}

export async function authMiddleware(req: AdminAuthRequest, res: Response, next: NextFunction): Promise<void> {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({ success: false, message: 'Authorization token missing or malformed' });
    return;
  }

  const token = authHeader.substring(7);
  try {
    const decoded = jwt.verify(token, ENV.JWT_SECRET, {
      algorithms: ['HS256'], issuer: 'royal-poker-admin', audience: 'royal-poker-operations',
    });
    if (typeof decoded !== 'object' || typeof decoded.exp !== 'number' || !decoded.sub || !/^[0-9a-f]{24}$/i.test(decoded.sub)) {
      res.status(401).json({ success: false, message: 'Invalid or expired session token' });
      return;
    }
    const account = await AdminUser.findOne({ _id: decoded.sub, isActive: true }).select('username role').lean();
    if (!account) {
      res.status(401).json({ success: false, message: 'Administrative session is unavailable' });
      return;
    }
    req.adminUser = { id: String(account._id), username: account.username, role: account.role };
  } catch (err) {
    res.status(401).json({ success: false, message: 'Invalid or expired session token' });
    return;
  }
  next();
}
