/**
 * Scores and ranks market items into flip opportunities.
 * This is the server-side intelligence that stays in the cloud;
 * copying the plugin does not replicate this logic.
 */

const MIN_VOLUME = 50;
const MIN_MARGIN_GP = 5;

function scoreItem(item) {
  const buy = item.buyPrice;
  const sell = item.sellPrice;
  if (!buy || !sell || buy <= 0 || sell <= buy) return null;

  const marginGp = sell - buy;
  const marginPercent = (marginGp / buy) * 100;
  const volume = item.volume || 0;

  if (volume < MIN_VOLUME || marginGp < MIN_MARGIN_GP) return null;

  // Confidence: blend of margin stability and volume strength
  const volumeScore = Math.min(volume / 5000, 1.0);
  const marginScore = Math.min(marginPercent / 10, 1.0);
  const priceStability = 1.0 - Math.min(Math.abs(sell - buy) / sell, 0.5);

  const confidence = 0.4 * volumeScore + 0.35 * marginScore + 0.25 * priceStability;

  const reasons = [];
  if (volumeScore > 0.7) reasons.push('High volume');
  if (marginPercent > 3) reasons.push('Strong margin');
  if (priceStability > 0.9) reasons.push('Stable spread');
  if (marginGp > 1000) reasons.push('Large GP spread');
  if (reasons.length === 0) reasons.push('Moderate opportunity');

  return {
    itemId: item.id,
    itemName: item.name,
    buyPrice: buy,
    sellPrice: sell,
    marginGp,
    marginPercent: Math.round(marginPercent * 100) / 100,
    confidence: Math.round(confidence * 100) / 100,
    volume,
    reason: reasons.join('; '),
  };
}

function rankOpportunities(items, limit = 20) {
  const scored = [];
  for (const item of items) {
    const opp = scoreItem(item);
    if (opp) scored.push(opp);
  }

  scored.sort((a, b) => {
    const aScore = a.confidence * 0.6 + Math.min(a.marginPercent / 10, 1) * 0.4;
    const bScore = b.confidence * 0.6 + Math.min(b.marginPercent / 10, 1) * 0.4;
    return bScore - aScore;
  });

  return scored.slice(0, limit);
}

module.exports = { scoreItem, rankOpportunities };
