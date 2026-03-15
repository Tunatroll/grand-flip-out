/**
 * Data store for users and API keys.
 *
 * Current implementation: in-memory (data lost on restart).
 * To switch to Postgres:
 *   1. npm install pg (or prisma / drizzle-orm)
 *   2. Set DATABASE_URL in env
 *   3. Replace the Map-based functions below with SQL queries
 *   4. Keep the same exported function signatures so nothing else changes
 *
 * Every function exported here is used by routes and middleware;
 * as long as the signatures stay the same, the rest of the server doesn't care
 * whether data lives in memory or a database.
 */

const bcrypt = require('bcryptjs');
const crypto = require('crypto');

// ─── In-memory tables ────────────────────────────────────────
const users = new Map();     // email -> user object
const apiKeys = new Map();   // keyHash -> key record

// ─── Helpers ─────────────────────────────────────────────────
function hashPassword(password) {
  return bcrypt.hashSync(password, 10);
}

function comparePassword(password, hash) {
  return bcrypt.compareSync(password, hash);
}

function generateApiKey() {
  return 'gfo_' + crypto.randomBytes(24).toString('base64url');
}

function hashApiKey(key) {
  return crypto.createHash('sha256').update(key).digest('hex');
}

// ─── Users ───────────────────────────────────────────────────
function createUser(email, password) {
  const lower = email.toLowerCase();
  if (users.has(lower)) return null;
  const user = {
    id: crypto.randomUUID(),
    email: lower,
    passwordHash: hashPassword(password),
    plan: 'free',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  users.set(lower, user);
  return user;
}

function findUserByEmail(email) {
  return users.get((email || '').toLowerCase()) || null;
}

function findUserById(id) {
  for (const u of users.values()) if (u.id === id) return u;
  return null;
}

function updateUserPlan(userId, plan) {
  const user = findUserById(userId);
  if (!user) return null;
  user.plan = plan;
  user.updatedAt = new Date().toISOString();
  return user;
}

// ─── API Keys ────────────────────────────────────────────────
function createApiKey(userId, label = 'Default') {
  const raw = generateApiKey();
  const kHash = hashApiKey(raw);
  const record = {
    id: crypto.randomUUID(),
    userId,
    keyHash: kHash,
    label,
    createdAt: new Date().toISOString(),
    lastUsedAt: null,
  };
  apiKeys.set(kHash, record);
  return { record, rawKey: raw };
}

function findKeyByRawKey(rawKey) {
  return apiKeys.get(hashApiKey(rawKey)) || null;
}

function getKeysByUserId(userId) {
  const out = [];
  for (const r of apiKeys.values()) if (r.userId === userId) out.push(r);
  return out.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
}

function revokeKey(keyId, userId) {
  for (const [hash, r] of apiKeys.entries()) {
    if (r.id === keyId && r.userId === userId) {
      apiKeys.delete(hash);
      return true;
    }
  }
  return false;
}

function touchKeyLastUsed(keyHash) {
  const r = apiKeys.get(keyHash);
  if (r) r.lastUsedAt = new Date().toISOString();
}

function getUserByApiKey(rawKey) {
  const rec = findKeyByRawKey(rawKey);
  if (!rec) return null;
  touchKeyLastUsed(rec.keyHash);
  return findUserById(rec.userId);
}

// ─── Plan helpers (for future billing) ───────────────────────
function getPlanLimits(plan) {
  const plans = {
    free:    { requestsPerMinute: 30, maxKeys: 2, opportunities: 10 },
    premium: { requestsPerMinute: 120, maxKeys: 10, opportunities: 50 },
  };
  return plans[plan] || plans.free;
}

function init() {
  return Promise.resolve();
}

const inMemory = {
  init,
  createUser: (...a) => Promise.resolve(createUser(...a)),
  findUserByEmail: (...a) => Promise.resolve(findUserByEmail(...a)),
  findUserById: (...a) => Promise.resolve(findUserById(...a)),
  updateUserPlan: (...a) => Promise.resolve(updateUserPlan(...a)),
  createApiKey: (...a) => Promise.resolve(createApiKey(...a)),
  findKeyByRawKey,
  getKeysByUserId: (...a) => Promise.resolve(getKeysByUserId(...a)),
  revokeKey: (...a) => Promise.resolve(revokeKey(...a)),
  getUserByApiKey: (...a) => Promise.resolve(getUserByApiKey(...a)),
  comparePassword,
  getPlanLimits,
};

if (process.env.DATABASE_URL) {
  try {
    module.exports = require('./store-pg');
  } catch {
    module.exports = inMemory;
  }
} else {
  module.exports = inMemory;
}
