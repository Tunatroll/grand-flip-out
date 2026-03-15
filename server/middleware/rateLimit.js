/**
 * Simple in-memory sliding-window rate limiter per IP and per API key.
 * Replace with Redis-backed limiter for multi-instance production.
 */

const DEFAULT_WINDOW_MS = 60_000;
const DEFAULT_MAX_REQUESTS = 60;

const hits = new Map();

function cleanExpired(bucket, now, windowMs) {
  while (bucket.length > 0 && bucket[0] <= now - windowMs) {
    bucket.shift();
  }
}

function createRateLimiter(opts = {}) {
  const windowMs = opts.windowMs || DEFAULT_WINDOW_MS;
  const max = opts.max || DEFAULT_MAX_REQUESTS;
  const keyFn = opts.keyFn || ((req) => req.ip);

  return function rateLimiter(req, res, next) {
    const key = keyFn(req);
    const now = Date.now();

    if (!hits.has(key)) hits.set(key, []);
    const bucket = hits.get(key);
    cleanExpired(bucket, now, windowMs);

    if (bucket.length >= max) {
      res.set('Retry-After', String(Math.ceil(windowMs / 1000)));
      return res.status(429).json({ error: 'Rate limit exceeded. Try again shortly.' });
    }

    bucket.push(now);
    res.set('X-RateLimit-Limit', String(max));
    res.set('X-RateLimit-Remaining', String(max - bucket.length));
    next();
  };
}

function apiKeyRateLimiter(opts = {}) {
  return createRateLimiter({
    windowMs: opts.windowMs || DEFAULT_WINDOW_MS,
    max: opts.max || DEFAULT_MAX_REQUESTS,
    keyFn: (req) => {
      const auth = req.headers.authorization;
      if (auth && auth.startsWith('Bearer ')) return 'key:' + auth.slice(7, 27);
      return 'ip:' + req.ip;
    },
  });
}

module.exports = { createRateLimiter, apiKeyRateLimiter };
