const express = require('express');
const path = require('path');
const cookieParser = require('cookie-parser');
const cors = require('cors');
const authRoutes = require('./routes/auth');
const keysRoutes = require('./routes/keys');
const planRoutes = require('./routes/plan');
const checkoutRoutes = require('./routes/checkout');
const marketRoutes = require('./routes/market');
const { requireApiKey } = require('./middleware/auth');
const { createRateLimiter, apiKeyRateLimiter } = require('./middleware/rateLimit');
const telemetry = require('./middleware/telemetry');
const market = require('./routes/market');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors({ origin: true, credentials: true }));

// Stripe webhook needs raw body for signature verification — mount before express.json()
const webhooks = require('./routes/webhooks');
app.post('/api/webhooks/stripe', express.raw({ type: 'application/json' }), webhooks.stripe);

app.use(express.json());
app.use(cookieParser());
app.use(telemetry);
app.use(telemetry.staticCache);

const authLimiter = createRateLimiter({ windowMs: 60_000, max: 15 });
const apiLimiter = apiKeyRateLimiter({ windowMs: 60_000, max: parseInt(process.env.API_RATE_LIMIT || '60', 10) });

app.use((req, res, next) => {
  if (req.path.startsWith('/api') || req.path.startsWith('/v1')) res.setHeader('Cache-Control', 'no-store');
  next();
});
app.use('/api/auth', authLimiter, authRoutes);
app.use('/api/user/keys', keysRoutes);
app.use('/api/user/plan', planRoutes);
app.use('/api/checkout', checkoutRoutes);
app.use('/api/market', apiLimiter, marketRoutes);
app.get('/api/opportunities', apiLimiter, requireApiKey, market.getOpportunitiesHandler);

// v1 versioned aliases — same handlers, stable contract
app.use('/v1/market', apiLimiter, marketRoutes);
app.get('/v1/opportunities', apiLimiter, requireApiKey, market.getOpportunitiesHandler);
app.use('/v1/auth', authLimiter, authRoutes);
app.use('/v1/user/keys', keysRoutes);
app.use('/v1/user/plan', planRoutes);

app.get('/health', (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.json({ status: 'ok', service: 'grandflipout-api' });
});

app.get('/api/health', (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.json({ status: 'ok', service: 'grandflipout-api' });
});

app.get('/v1/health', (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.json({ status: 'ok', service: 'grandflipout-api' });
});

const websiteDir = process.env.WEBSITE_DIR || path.join(__dirname, '..', 'website');
app.use(express.static(websiteDir));

app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: 'Internal server error' });
});

const store = require('./store');
store.init()
  .then(() => {
    app.listen(PORT, () => {
      console.log(`Grand Flip Out API listening on port ${PORT}`);
    });
  })
  .catch((err) => {
    console.error('Store init failed:', err);
    process.exit(1);
  });
