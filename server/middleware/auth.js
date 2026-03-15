const jwt = require('jsonwebtoken');
const store = require('../store');

const JWT_SECRET = process.env.JWT_SECRET || 'change-me-in-production';

function signToken(user) {
  return jwt.sign(
    { sub: user.id, email: user.email },
    JWT_SECRET,
    { expiresIn: '7d' }
  );
}

async function verifyToken(token) {
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    return await store.findUserById(payload.sub);
  } catch {
    return null;
  }
}

async function requireWebAuth(req, res, next) {
  try {
    const token = req.cookies?.token || req.headers.authorization?.replace('Bearer ', '');
    if (!token) {
      return res.status(401).json({ error: 'Not authenticated' });
    }
    const user = await verifyToken(token);
    if (!user) return res.status(401).json({ error: 'Invalid or expired token' });
    req.user = user;
    next();
  } catch (e) {
    next(e);
  }
}

async function requireApiKey(req, res, next) {
  try {
    const auth = req.headers.authorization;
    const key = auth && auth.startsWith('Bearer ') ? auth.slice(7).trim() : null;
    if (!key) {
      return res.status(401).json({ error: 'Missing or invalid Authorization header. Use: Bearer <api_key>' });
    }
    const user = await store.getUserByApiKey(key);
    if (!user) return res.status(401).json({ error: 'Invalid or revoked API key' });
    req.user = user;
    next();
  } catch (e) {
    next(e);
  }
}

module.exports = {
  signToken,
  verifyToken,
  requireWebAuth,
  requireApiKey,
};
