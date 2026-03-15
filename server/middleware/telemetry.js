/**
 * Request telemetry: logs method, path, status, duration; sets X-Response-Time and Cache-Control where appropriate.
 */

function telemetry(req, res, next) {
  const start = Date.now();
  const origEnd = res.end;
  res.end = function (...args) {
    const duration = Date.now() - start;
    if (!res.headersSent) {
      res.setHeader('X-Response-Time', `${duration}ms`);
    }
    origEnd.apply(res, args);
    const level = res.statusCode >= 500 ? 'ERROR' : res.statusCode >= 400 ? 'WARN' : 'INFO';
    console.log(
      `[${level}] ${req.method} ${req.originalUrl} ${res.statusCode} ${duration}ms` +
      (req.user ? ` user=${req.user.id}` : '')
    );
  };
  next();
}

function staticCache(req, res, next) {
  if (req.path.match(/\.(css|js|ico|png|svg|woff2?)$/)) {
    res.setHeader('Cache-Control', 'public, max-age=300');
  }
  next();
}

module.exports = telemetry;
module.exports.staticCache = staticCache;
