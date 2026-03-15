const express = require('express');
const store = require('../store');
const { requireWebAuth } = require('../middleware/auth');

const router = express.Router();

router.get('/', requireWebAuth, async (req, res, next) => {
  try {
    const keys = (await store.getKeysByUserId(req.user.id)).map((k) => ({
      id: k.id,
      label: k.label,
      createdAt: k.created_at || k.createdAt,
      lastUsedAt: k.last_used_at || k.lastUsedAt,
    }));
    res.json({ keys });
  } catch (e) {
    next(e);
  }
});

router.post('/', requireWebAuth, async (req, res, next) => {
  try {
    let label = (req.body && typeof req.body.label === 'string') ? req.body.label.trim() : '';
    if (!label) label = 'Default';
    if (label.length > 64) {
      return res.status(400).json({ error: 'Label must be 64 characters or fewer' });
    }
    const { record, rawKey } = await store.createApiKey(req.user.id, label);
    res.status(201).json({
      key: rawKey,
      id: record.id,
      label: record.label,
      createdAt: record.createdAt || record.created_at,
    });
  } catch (e) {
    next(e);
  }
});

router.delete('/:id', requireWebAuth, async (req, res, next) => {
  try {
    const ok = await store.revokeKey(req.params.id, req.user.id);
    if (!ok) return res.status(404).json({ error: 'Key not found' });
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});

module.exports = router;
