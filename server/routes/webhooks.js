/**
 * Stripe webhook handler. Mounted before express.json() so req.body stays raw.
 * Env: STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET.
 */
const store = require('../store');

function stripeHandler(req, res) {
  const secret = process.env.STRIPE_WEBHOOK_SECRET;
  const sig = req.headers['stripe-signature'];
  const body = req.body;

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
    console.warn('Stripe webhook sig verify failed:', err.message);
    res.status(400).send('Webhook signature verification failed');
    return;
  }

  handleEvent(event, stripe).catch((err) => {
    console.error('Webhook event handling error:', err.message);
  });

  res.status(200).send();
}

async function handleEvent(event, stripe) {
  if (event.type === 'checkout.session.completed') {
    const session = event.data.object;
    const email = session.customer_email || session.customer_details?.email;
    if (email) {
      const user = await store.findUserByEmail(email);
      if (user) {
        await store.updateUserPlan(user.id, 'premium');
        console.log(`Upgraded ${email} to premium`);
      }
    }
  } else if (event.type === 'customer.subscription.deleted') {
    const sub = event.data.object;
    const email = await resolveCustomerEmail(stripe, sub.customer);
    if (email) {
      const user = await store.findUserByEmail(email);
      if (user) {
        await store.updateUserPlan(user.id, 'free');
        console.log(`Downgraded ${email} to free (subscription ended)`);
      }
    }
  } else if (event.type === 'invoice.payment_failed') {
    const invoice = event.data.object;
    const email = invoice.customer_email;
    if (email) {
      console.warn(`Payment failed for ${email}`);
    }
  }
}

async function resolveCustomerEmail(stripe, customerId) {
  if (!customerId) return null;
  try {
    const customer = await stripe.customers.retrieve(customerId);
    return customer.email || null;
  } catch {
    return null;
  }
}

module.exports = { stripe: stripeHandler };
