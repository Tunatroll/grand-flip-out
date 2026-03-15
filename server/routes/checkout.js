/**
 * Stripe Checkout integration.
 * Env: STRIPE_SECRET_KEY, STRIPE_PRICE_ID, BASE_URL (optional).
 */
const express = require('express');
const { requireWebAuth } = require('../middleware/auth');

const router = express.Router();

function getStripeConfig() {
  return {
    secretKey: process.env.STRIPE_SECRET_KEY || '',
    priceId: process.env.STRIPE_PRICE_ID || '',
    baseUrl: process.env.BASE_URL || 'https://grandflipout.com',
  };
}

router.get('/config', (_req, res) => {
  const cfg = getStripeConfig();
  res.json({ configured: Boolean(cfg.priceId && cfg.secretKey) });
});

router.post('/session', requireWebAuth, async (req, res, next) => {
  const cfg = getStripeConfig();
  if (!cfg.priceId || !cfg.secretKey) {
    return res.status(503).json({ error: 'Checkout not configured.' });
  }
  let Stripe;
  try {
    Stripe = require('stripe');
  } catch {
    return res.status(503).json({ error: 'Stripe package not available.' });
  }
  const stripe = new Stripe(cfg.secretKey);
  try {
    const session = await stripe.checkout.sessions.create({
      customer_email: req.user.email,
      line_items: [{ price: cfg.priceId, quantity: 1 }],
      mode: 'subscription',
      success_url: req.body?.success_url || (cfg.baseUrl + '/dashboard.html?upgraded=1'),
      cancel_url: req.body?.cancel_url || (cfg.baseUrl + '/pricing.html'),
      metadata: { user_id: req.user.id },
    });
    res.json({ url: session.url });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
