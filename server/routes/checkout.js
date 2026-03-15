/**
 * Create a Stripe Checkout Session so the frontend can redirect to Stripe.
 * Set STRIPE_SECRET_KEY and STRIPE_PRICE_ID. Success/cancel URLs can be overridden via body or env.
 */
const express = require('express');
const { requireWebAuth } = require('../middleware/auth');

const router = express.Router();
const priceId = process.env.STRIPE_PRICE_ID;
const baseUrl = process.env.BASE_URL || '';

router.post('/session', requireWebAuth, async (req, res, next) => {
  if (!priceId || !process.env.STRIPE_SECRET_KEY) {
    return res.status(503).json({ error: 'Checkout not configured. Set STRIPE_SECRET_KEY and STRIPE_PRICE_ID.' });
  }
  let Stripe;
  try {
    Stripe = require('stripe');
  } catch {
    return res.status(503).json({ error: 'Stripe package not installed. Run: npm install stripe' });
  }
  const stripe = new Stripe(process.env.STRIPE_SECRET_KEY);
  const successUrl = req.body?.success_url || (baseUrl + '/dashboard.html?upgraded=1');
  const cancelUrl = req.body?.cancel_url || (baseUrl + '/pricing.html');
  try {
    const session = await stripe.checkout.sessions.create({
      customer_email: req.user.email,
      line_items: [{ price: priceId, quantity: 1 }],
      mode: 'subscription',
      success_url: successUrl,
      cancel_url: cancelUrl,
      metadata: { user_id: req.user.id },
    });
    res.json({ url: session.url });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
