/**
 * Server-side alert rule engine.
 * Tracks price/volume history across polling cycles and flags notable changes.
 * Alerts are returned alongside opportunities so the plugin can display or notify.
 */

const priceHistory = new Map();
const HISTORY_WINDOW = 10;

function recordSnapshot(items) {
  const now = Date.now();
  for (const item of items) {
    if (!priceHistory.has(item.id)) priceHistory.set(item.id, []);
    const hist = priceHistory.get(item.id);
    hist.push({ t: now, buy: item.buyPrice, sell: item.sellPrice, vol: item.volume });
    while (hist.length > HISTORY_WINDOW) hist.shift();
  }
}

function detectAlerts(items) {
  const alerts = [];
  for (const item of items) {
    const hist = priceHistory.get(item.id);
    if (!hist || hist.length < 3) continue;

    const prev = hist[hist.length - 2];
    const older = hist[0];

    if (prev.vol > 0 && item.volume > 0) {
      const volDrop = (prev.vol - item.volume) / prev.vol;
      if (volDrop > 0.30) {
        alerts.push({
          itemId: item.id,
          itemName: item.name,
          type: 'volume_drop',
          severity: 'warning',
          message: `Volume dropped ${Math.round(volDrop * 100)}% since previous poll`,
          value: item.volume,
          previousValue: prev.vol,
        });
      }
    }

    if (older.sell > 0 && item.sellPrice > 0) {
      const priceDrift = Math.abs(item.sellPrice - older.sell) / older.sell;
      if (priceDrift > 0.05) {
        const direction = item.sellPrice > older.sell ? 'up' : 'down';
        alerts.push({
          itemId: item.id,
          itemName: item.name,
          type: 'price_drift',
          severity: 'info',
          message: `Sell price shifted ${direction} ${Math.round(priceDrift * 100)}% over ${hist.length} polls`,
          value: item.sellPrice,
          previousValue: older.sell,
        });
      }
    }

    const margin = item.sellPrice - item.buyPrice;
    if (prev.sell && prev.buy) {
      const prevMargin = prev.sell - prev.buy;
      if (prevMargin > 0 && margin > 0) {
        const marginShrink = (prevMargin - margin) / prevMargin;
        if (marginShrink > 0.40) {
          alerts.push({
            itemId: item.id,
            itemName: item.name,
            type: 'margin_collapse',
            severity: 'warning',
            message: `Margin shrank ${Math.round(marginShrink * 100)}% since last poll`,
            value: margin,
            previousValue: prevMargin,
          });
        }
      }
    }

    if (hist.length >= 5) {
      const recentPrices = hist.slice(-5).map((h) => h.sell);
      const avg = recentPrices.reduce((a, b) => a + b, 0) / recentPrices.length;
      const variance = recentPrices.reduce((s, p) => s + (p - avg) ** 2, 0) / recentPrices.length;
      const stdDev = Math.sqrt(variance);
      if (avg > 0 && stdDev / avg < 0.005 && margin > 0) {
        alerts.push({
          itemId: item.id,
          itemName: item.name,
          type: 'stale_offer',
          severity: 'info',
          message: 'Prices very stable over recent polls; may indicate low activity or stale offers',
          value: item.sellPrice,
          previousValue: null,
        });
      }
    }
  }
  return alerts;
}

module.exports = { recordSnapshot, detectAlerts };
