# API Contract (Plugin <-> Backend)

## Base settings

- Base URL from `GrandFlipOutApiConfig.apiBaseUrl`
- Endpoint path from `GrandFlipOutApiConfig.apiEndpoint`
- Opportunities endpoint from `GrandFlipOutApiConfig.opportunitiesEndpoint` (optional fallback source)
- Optional query: `?strategy=low_risk|high_volume|high_margin` (plugin sends when user selects a non-default strategy in config)
- Auth header:
  - `Authorization: Bearer <apiKey>` when key is present

## Expected response shape (current plugin parser)

```json
{
  "items": [
    {
      "id": 4151,
      "name": "Abyssal whip",
      "buyPrice": 2550000,
      "sellPrice": 2575000,
      "highPrice": 2580000,
      "lowPrice": 2540000,
      "volume": 1300
    }
  ],
  "opportunities": [
    {
      "itemId": 4151,
      "itemName": "Abyssal whip",
      "buyPrice": 2550000,
      "sellPrice": 2575000,
      "marginGp": 25000,
      "marginPercent": 0.98,
      "confidence": 0.84,
      "volume": 1300,
      "reason": "High spread stability and volume"
    }
  ],
  "timestamp": 1710000000000,
  "source": "grandflipout"
}
```

## Error handling expectations

- `200`: parse and render market data
- `401/403`: surface auth error in plugin UI
- `>=400`: show server status error in plugin UI
- network failure: show network error in plugin UI

## Opportunity alert contract notes

- `marginPercent` should be numeric percent (e.g. `2.5` means 2.5%)
- `confidence` should be normalized decimal (`0.0` to `1.0`)
- Plugin alerting uses local thresholds:
  - minimum margin %
  - minimum confidence %
  - cooldown minutes

## Recommended v1 endpoint layout

- `GET /v1/market/items`
- `GET /v1/market/opportunities`
- `GET /v1/user/plan`
- `GET /v1/user/profile`

## Persistence format (plugin local)

- Trade history: versioned JSON envelope (`schemaVersion`, `records`)
- Flip logs: versioned JSON envelope (`schemaVersion`, `logs`)
- Backward compatibility: plugin can still read prior plain-list JSON payloads

## Backward compatibility guidance

- Keep existing fields stable
- Additive fields only where possible
- If breaking changes are needed, deploy new versioned route
