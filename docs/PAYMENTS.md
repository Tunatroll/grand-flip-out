# Payments and receiving money

This doc describes how to let users pay and how you receive payment, using **Stripe** (or a similar provider). The backend is already set up for plans (`free` / `premium`); you add Stripe and connect it.

## How it works

1. **User signs up** on your site (free). They get plan `free` and can create an API key.
2. **User upgrades** — On your Pricing page they click “Upgrade” and go to **Stripe Checkout**. They enter payment details on Stripe’s page (you never see the card).
3. **Stripe sends a webhook** to your server (`POST /api/webhooks/stripe`) when payment succeeds.
4. **Your server** verifies the webhook with Stripe, finds the user (by email from the Stripe event), and sets their plan to `premium` in your database.
5. **User gets premium** — Higher rate limits and limits (see `store.js` plan limits). No change in the plugin; the API enforces limits.

You receive the money in your **Stripe account**. You then pay out from Stripe to your bank (Stripe → Dashboard → Payouts).

## What you need to do

### 1. Create a Stripe account

- Go to [stripe.com](https://stripe.com) and sign up.
- Complete identity verification so you can receive payouts.
- In Dashboard → Developers → API keys: get your **Publishable key** and **Secret key** (and optionally **Webhook signing secret**).

### 2. Add a Stripe product and price

- In Stripe Dashboard: Products → Add product (e.g. “Grand Flip Out Premium”).
- Add a price (e.g. monthly or yearly). Note the **Price ID** (e.g. `price_xxx`).

### 3. Configure the server

Set these environment variables (e.g. in Railway):

- `STRIPE_SECRET_KEY` — Your Stripe secret key (starts with `sk_`).
- `STRIPE_WEBHOOK_SECRET` — From Stripe Dashboard → Developers → Webhooks → Add endpoint (see below). Signing secret (starts with `whsec_`).
- `STRIPE_PRICE_ID` — Optional; the Price ID for “Premium” if you use the built-in checkout link.

### 4. Add the webhook endpoint in Stripe

- Stripe Dashboard → Developers → Webhooks → Add endpoint.
- URL: `https://grandflipout.com/api/webhooks/stripe` (or your API URL).
- Events to send: `checkout.session.completed`, and optionally `customer.subscription.updated`, `customer.subscription.deleted`.
- Copy the **Signing secret** and set it as `STRIPE_WEBHOOK_SECRET`.

### 5. Pricing page on your site

- Add a “Upgrade to Premium” or “Subscribe” button that links to Stripe Checkout.
- Option A: Use Stripe Checkout (recommended). Your backend can expose `GET /api/checkout/session?email=...` that creates a Stripe Checkout Session and returns `{ url }`; the button redirects to that URL.
- Option B: Use a direct link to Stripe Payment Links (you create the link in Stripe Dashboard and paste it on the page).

The server already has a webhook handler at `POST /api/webhooks/stripe`. When `STRIPE_WEBHOOK_SECRET` is set and the `stripe` package is installed, it verifies the event and upgrades the user’s plan to `premium` when payment succeeds. If the secret is not set, the route returns 200 and does nothing (so Stripe does not retry).

### 6. Optional: create checkout session endpoint

For “Upgrade” buttons you can add a route that creates a Stripe Checkout Session and returns the session URL. See the server code or add one that uses `stripe.checkout.sessions.create` with `customer_email`, `line_items: [{ price: STRIPE_PRICE_ID }]`, and `success_url` / `cancel_url` pointing back to your site. Redirect the user to `session.url`.

## Compliance and security

- **You never see card numbers** — Stripe handles all payment data (PCI compliant).
- **Webhook verification** — The server only upgrades a user when Stripe’s signature is valid.
- **No game credentials** — Payment is for your service (API/premium); no RuneScape or Jagex involvement. RuneLite compliant.

## Payouts to you

- Stripe pays out to your bank on the schedule you choose (e.g. daily, weekly). Dashboard → Settings → Payouts.
- Fees: Stripe charges per transaction; see [Stripe pricing](https://stripe.com/pricing).

## If you use another provider

- **Paddle** — Similar: product, webhook, update user plan in your DB. Implement a webhook route and call `store.updateUserPlan(userId, 'premium')` when payment is confirmed.
- **PayPal** — Same idea: IPN or webhooks, then update plan. Map PayPal “payment completed” to the user (e.g. by email or custom id) and set plan to `premium`.

The important part is: when payment is confirmed, call your store’s `updateUserPlan(userId, 'premium')` (and optionally `updateUserPlan(userId, 'free')` on cancel/refund).
