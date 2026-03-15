const express = require('express');
const store = require('../store');
const { signToken, requireWebAuth } = require('../middleware/auth');

const router = express.Router();

router.post('/signup', async (req, res, next) => {
  try {
    const { email, password } = req.body || {};
    if (!email || typeof email !== 'string' || !email.trim()) {
      return res.status(400).json({ error: 'Email is required' });
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      return res.status(400).json({ error: 'Invalid email format' });
    }
    if (!password || typeof password !== 'string' || password.length < 8) {
      return res.status(400).json({ error: 'Password must be at least 8 characters' });
    }
    const user = await store.createUser(email.trim(), password);
    if (!user) return res.status(409).json({ error: 'Email already registered' });
    const token = signToken(user);
    const { passwordHash, ...safe } = user;
    const secure = process.env.NODE_ENV === 'production';
    res
      .cookie('token', token, { httpOnly: true, secure, maxAge: 7 * 24 * 60 * 60 * 1000, sameSite: 'lax' })
      .status(201)
      .json({ user: safe, token });
  } catch (e) {
    next(e);
  }
});

router.post('/login', async (req, res, next) => {
  try {
    const { email, password } = req.body || {};
    if (!email || typeof email !== 'string' || !password || typeof password !== 'string') {
      return res.status(400).json({ error: 'Email and password required' });
    }
    const user = await store.findUserByEmail(email.trim());
    if (!user || !store.comparePassword(password, user.passwordHash)) {
      return res.status(401).json({ error: 'Invalid email or password' });
    }
    const token = signToken(user);
    const { passwordHash, ...safe } = user;
    const secure = process.env.NODE_ENV === 'production';
    res
      .cookie('token', token, { httpOnly: true, secure, maxAge: 7 * 24 * 60 * 60 * 1000, sameSite: 'lax' })
      .json({ user: safe, token });
  } catch (e) {
    next(e);
  }
});

router.post('/logout', (_req, res) => {
  res.clearCookie('token').json({ ok: true });
});

router.get('/me', requireWebAuth, (req, res) => {
  const { passwordHash, ...safe } = req.user;
  res.json(safe);
});

module.exports = router;
