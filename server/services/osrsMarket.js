/**
 * Fetches real-time GE prices from the OSRS Wiki API and caches them.
 * https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices
 *
 * The Wiki API requires a descriptive User-Agent; using project name + contact.
 */

const PRICES_URL = 'https://prices.runescape.wiki/api/v1/osrs/latest';
const MAPPING_URL = 'https://prices.runescape.wiki/api/v1/osrs/mapping';
const VOLUMES_URL = 'https://prices.runescape.wiki/api/v1/osrs/volumes';
const USER_AGENT = 'GrandFlipOut/1.0 (grandflipout.com)';
const CACHE_TTL_MS = 60_000;

let priceCache = null;
let priceCacheTime = 0;
let mappingCache = null;
let volumeCache = null;
let volumeCacheTime = 0;

async function fetchJson(url) {
  const res = await fetch(url, {
    headers: { 'User-Agent': USER_AGENT },
  });
  if (!res.ok) throw new Error(`Wiki API ${res.status}: ${res.statusText}`);
  return res.json();
}

async function getMapping() {
  if (mappingCache) return mappingCache;
  const data = await fetchJson(MAPPING_URL);
  const map = new Map();
  for (const item of data) {
    map.set(item.id, item);
  }
  mappingCache = map;
  return map;
}

async function getVolumes() {
  if (volumeCache && Date.now() - volumeCacheTime < CACHE_TTL_MS * 5) return volumeCache;
  try {
    const data = await fetchJson(VOLUMES_URL);
    volumeCache = data.data || data;
    volumeCacheTime = Date.now();
  } catch {
    if (!volumeCache) volumeCache = {};
  }
  return volumeCache;
}

async function getLatestPrices() {
  if (priceCache && Date.now() - priceCacheTime < CACHE_TTL_MS) return priceCache;
  const data = await fetchJson(PRICES_URL);
  priceCache = data.data || data;
  priceCacheTime = Date.now();
  return priceCache;
}

async function getMarketItems(limit = 200) {
  const [prices, mapping, volumes] = await Promise.all([
    getLatestPrices(),
    getMapping(),
    getVolumes(),
  ]);

  const items = [];
  for (const [idStr, price] of Object.entries(prices)) {
    const id = parseInt(idStr, 10);
    const meta = mapping.get(id);
    if (!meta || !price.high || !price.low) continue;

    const vol = volumes[idStr] || 0;
    items.push({
      id,
      name: meta.name,
      buyPrice: price.low,
      sellPrice: price.high,
      highPrice: price.high,
      lowPrice: price.low,
      volume: typeof vol === 'number' ? vol : 0,
    });
  }

  items.sort((a, b) => b.volume - a.volume);
  return items.slice(0, limit);
}

module.exports = { getMarketItems, getLatestPrices, getMapping, getVolumes };
