/**
 * Strategy presets filter and re-rank opportunities for different play styles.
 * The plugin can request a strategy via query param: ?strategy=low_risk
 */

const PRESETS = {
  default: {
    label: 'Default (balanced)',
    filter: () => true,
    sort: (a, b) => {
      const aS = a.confidence * 0.6 + Math.min(a.marginPercent / 10, 1) * 0.4;
      const bS = b.confidence * 0.6 + Math.min(b.marginPercent / 10, 1) * 0.4;
      return bS - aS;
    },
  },
  low_risk: {
    label: 'Low risk (high volume, stable)',
    filter: (opp) => opp.volume >= 1000 && opp.confidence >= 0.5,
    sort: (a, b) => b.volume - a.volume,
  },
  high_volume: {
    label: 'High volume (fastest flips)',
    filter: (opp) => opp.volume >= 500,
    sort: (a, b) => b.volume - a.volume,
  },
  high_margin: {
    label: 'High margin (biggest profit per flip)',
    filter: (opp) => opp.marginGp >= 100,
    sort: (a, b) => b.marginGp - a.marginGp,
  },
};

function getStrategyNames() {
  return Object.entries(PRESETS).map(([key, val]) => ({ key, label: val.label }));
}

function applyStrategy(opportunities, strategyKey) {
  const preset = PRESETS[strategyKey] || PRESETS.default;
  const filtered = opportunities.filter(preset.filter);
  filtered.sort(preset.sort);
  return { strategy: preset.label, opportunities: filtered };
}

module.exports = { getStrategyNames, applyStrategy, PRESETS };
