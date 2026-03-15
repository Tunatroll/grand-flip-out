# Value Protection (RuneLite Compliant)

This document explains how Grand Flip Out keeps features differentiated and defensible while remaining fully compliant with RuneLite and Jagex policies.

## Principle: Value in the Cloud

- **The plugin is a thin client.** It does not implement proprietary market scoring, ranking, or prediction logic. All "intelligence" (ranked opportunities, confidence scores, reasons, live market aggregation) is produced by **your backend** at grandflipout.com and delivered via your API.
- **Copying the plugin does not copy your product.** Someone who forks or reuses the plugin code gets:
  - A generic UI and local trade-history/profit tracking (standard arithmetic, FIFO, etc.).
  - A client that *calls an API* for live data and opportunities.
- **They cannot replicate your backend** without your server, your data, your algorithms, and your terms of service. The plugin is the front end; the value is in your service.

## What Lives Where

| Feature | Location | Stealable by copying plugin? |
|--------|----------|------------------------------|
| Ranked opportunities, confidence, reasons | Backend (grandflipout.com) | No |
| Live market item list / aggregation | Backend (or your data pipeline) | No |
| API key / auth, rate limits, ToS | Your server | No |
| Trade history, FIFO, profit breakdown | Plugin (local only) | Yes (generic logic) |
| UI layout, tabs, alerts thresholds | Plugin | Yes (cosmetic) |

So the **features nobody can use on their own** are the ones that depend on your API: ranked opportunities, live market data from your pipeline, and any future server-side analysis. The plugin only displays and reacts to that data.

## Compliance-Friendly Protection

- **No obfuscation.** The plugin source can remain open and reviewable. RuneLite and Jagex allow this; hiding behavior is not required for protection.
- **No locked binaries.** You are not relying on closed-source native code or encrypted payloads that obscure what the client does.
- **Clear dependency.** The plugin explicitly depends on your API (config, UI copy, docs). Users and reviewers see that "Grand Flip Out" is the client for the grandflipout.com service. That makes it harder for others to rebrand and resell your service without permission.
- **Terms of service.** Your website/API ToS can prohibit unauthorized reuse of your API, scraping, or resale of your data. The plugin does not need to enforce this; your backend and legal terms do.

## Recommended Practices

1. **Keep intelligence on the server.** Do not move opportunity scoring, confidence models, or ranking algorithms into the plugin. The plugin should only parse and display what the API returns.
2. **Brand the client.** Use "Powered by grandflipout.com" or similar in the UI so the link between plugin and service is obvious.
3. **Require API configuration for premium features.** Ranked opportunities and live market data can require a valid base URL (and optionally API key). Local-only features (trade history, flip logs) still work without your API.
4. **Version your API.** Use versioned routes and response shapes (see API_CONTRACT.md) so you can evolve your backend without breaking the plugin, and so the plugin remains a thin client that adapts to your API.

This strategy keeps the plugin compliant while ensuring that the features that differentiate Grand Flip Out—market analysis, rankings, and live data—remain tied to your backend and cannot be "stolen" by copying the plugin alone.
