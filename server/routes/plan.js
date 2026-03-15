const express = require('express');
const { requireWebAuth } = require('../middleware/auth');
const store = require('../store');

const router = express.Router();

router.get('/', requireWebAuth, (req, res) => {
  const limits = store.getPlanLimits(req.user.plan);
  res.json({
    plan: req.user.plan,
    limits,
  });
});

module.exports = router;
