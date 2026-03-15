const express = require('express');
const { requireApiKey } = require('../middleware/auth');
const { getMarketItems } = require('../services/osrsMarket');
const { rankOpportunities } = require('../services/scorer');
const { recordSnapshot, detectAlerts } = require('../services/alerts');
const { applyStrategy, getStrategyNames, PRESETS } = require('../services/strategies');

const VALID_STRATEGIES = new Set(Object.keys(PRESETS));

const router = express.Router();

function validateStrategy(strategy, res) {
  if (strategy && !VALID_STRATEGIES.has(strategy)) {
    res.status(400).json({ error: `Unknown strategy "${strategy}". Valid: ${[...VALID_STRATEGIES].join(', ')}` });
    return false;
  }
  return true;
}

function applyStrategyIfPresent(opportunities, strategy) {
  if (!strategy) return { opportunities, strategyLabel: null };
  const result = applyStrategy(opportunities, strategy);
  return { opportunities: result.opportunities, strategyLabel: result.strategy };
}

router.get('/', requireApiKey, async (req, res, next) => {
  try {
    const strategy = req.query.strategy;
    if (!validateStrategy(strategy, res)) return;

    const items = await getMarketItems(200);
    recordSnapshot(items);
    const alerts = detectAlerts(items);

    let opportunities = rankOpportunities(items, 40);
    const { opportunities: filtered, strategyLabel } = applyStrategyIfPresent(opportunities, strategy);

    res.json({
      items,
      opportunities: filtered.slice(0, 20),
      alerts,
      strategy: strategyLabel,
      timestamp: Date.now(),
      source: 'grandflipout',
    });
  } catch (err) {
    next(err);
  }
});

router.get('/opportunities', requireApiKey, async (req, res, next) => {
  try {
    const strategy = req.query.strategy;
    if (!validateStrategy(strategy, res)) return;

    const items = await getMarketItems(500);
    recordSnapshot(items);

    let opportunities = rankOpportunities(items, 60);
    const { opportunities: filtered, strategyLabel } = applyStrategyIfPresent(opportunities, strategy);

    res.json({ opportunities: filtered.slice(0, 30), strategy: strategyLabel });
  } catch (err) {
    next(err);
  }
});

router.get('/strategies', (_req, res) => {
  res.json(getStrategyNames());
});

async function getOpportunitiesHandler(req, res, next) {
  try {
    const strategy = req.query.strategy;
    if (!validateStrategy(strategy, res)) return;

    const items = await getMarketItems(500);
    let opportunities = rankOpportunities(items, 60);
    const { opportunities: filtered, strategyLabel } = applyStrategyIfPresent(opportunities, strategy);

    res.json({ opportunities: filtered.slice(0, 30), strategy: strategyLabel });
  } catch (err) {
    next(err);
  }
}

module.exports = router;
module.exports.getOpportunitiesHandler = getOpportunitiesHandler;
