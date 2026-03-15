/**
 * Stripe webhook handler. Run before express.json() so req.body stays raw for signature verification.
 * Set STRIPE_WEBHOOK_SECRET and install optional dependency "stripe" to enable.
 */
const store = require('../store');

function stripeHandler(req, res) {
  const secret = process.env.STRIPE_WEBHOOK_SECRET;
  const sig = req.headers['stripe-signature'];
  const body = req.body; // Buffer when using express.raw({ type: 'application/json' })

  if (!secret || !sig || !body) {
    res.status(200).send();
    return;
  }

  let Stripe;
  try {
    Stripe = require('stripe');
  } catch {
    res.status(200).send();
    return;
  }

  const stripe = new Stripe(process.env.STRIPE_SECRET_KEY || '');
  let event;
  try {
    event = stripe.webhooks.constructEvent(body, sig, secret);
  } catch (err) {
    console.warn('Stripe webhook signature verification failed:', err.message);
    res.status(400).send('Webhook signature verification failed');
    return;
  }

  if (event.type === 'checkout.session.completed') {
    const session = event.data.object;
    const email = session.customer_email || session.customer_details?.email;
    if (email) {
      store.findUserByEmail(email).then((user) => {
        if (user) store.updateUserPlan(user.id, 'premium');
      }).catch(() => {});
    }
  } else if (event.type === 'customer.subscription.updated' || event.type === 'customer.subscription.deleted') {
    // Optional: resolve customer to email and update plan (e.g. downgrade on delete)
    const sub = event.data.object;
    if (sub.status === 'active' && event.type === 'customer.subscription.updated') {
      // Could fetch customer email via stripe.customers.retrieve(sub.customer) then updateUserPlan
    } else if (event.type === 'customer.subscription.deleted') {
      // Could look up user by stripe_customer_id and set plan to 'free'
    }
  }

  res.status(200).send();
}

module.exports = { stripe: stripeHandler };
