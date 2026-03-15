/**
 * Postgres-backed store. Used when DATABASE_URL is set.
 * Run server/db/schema.sql once before first use.
 */

const bcrypt = require('bcryptjs');
const crypto = require('crypto');
let pool;

async function init() {
  const { Pool } = require('pg');
  const url = process.env.DATABASE_URL;
  const ssl = url && (url.includes('railway') || url.includes('ssl') || url.startsWith('postgresql://')) ? { rejectUnauthorized: false } : false;
  pool = new Pool({ connectionString: url, ssl });
  return pool.query('SELECT 1').then(() => {}).catch((err) => { pool = null; throw err; });
}

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

async function createUser(email, password) {
  const id = crypto.randomUUID();
  const lower = email.toLowerCase();
  const passwordHash = hashPassword(password);
  try {
    await pool.query(
      'INSERT INTO users (id, email, password_hash, plan) VALUES ($1, $2, $3, $4)',
      [id, lower, passwordHash, 'free']
    );
    return { id, email: lower, passwordHash, plan: 'free', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
  } catch (e) {
    if (e.code === '23505') return null;
    throw e;
  }
}

function rowToUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    email: row.email,
    passwordHash: row.password_hash,
    plan: row.plan,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

async function findUserByEmail(email) {
  const r = await pool.query('SELECT * FROM users WHERE email = $1', [(email || '').toLowerCase()]);
  return rowToUser(r.rows[0]);
}

async function findUserById(id) {
  const r = await pool.query('SELECT * FROM users WHERE id = $1', [id]);
  return rowToUser(r.rows[0]);
}

async function createApiKey(userId, label = 'Default') {
  const raw = generateApiKey();
  const keyHash = hashApiKey(raw);
  const id = crypto.randomUUID();
  await pool.query(
    'INSERT INTO api_keys (id, user_id, key_hash, label) VALUES ($1, $2, $3, $4)',
    [id, userId, keyHash, label]
  );
  return { record: { id, userId, keyHash, label, createdAt: new Date().toISOString(), lastUsedAt: null }, rawKey: raw };
}

async function getKeysByUserId(userId) {
  const r = await pool.query('SELECT id, user_id, label, created_at, last_used_at FROM api_keys WHERE user_id = $1 ORDER BY created_at DESC', [userId]);
  return r.rows;
}

async function revokeKey(keyId, userId) {
  const r = await pool.query('DELETE FROM api_keys WHERE id = $1 AND user_id = $2', [keyId, userId]);
  return r.rowCount > 0;
}

async function getUserByApiKey(rawKey) {
  const keyHash = hashApiKey(rawKey);
  const keyRow = await pool.query('SELECT * FROM api_keys WHERE key_hash = $1', [keyHash]);
  const rec = keyRow.rows[0];
  if (!rec) return null;
  await pool.query('UPDATE api_keys SET last_used_at = $1 WHERE key_hash = $2', [new Date().toISOString(), keyHash]);
  return findUserById(rec.user_id);
}

async function updateUserPlan(userId, plan) {
  await pool.query('UPDATE users SET plan = $1, updated_at = $2 WHERE id = $3', [plan, new Date().toISOString(), userId]);
  return findUserById(userId);
}

function getPlanLimits(plan) {
  const plans = { free: { requestsPerMinute: 30, maxKeys: 2, opportunities: 10 }, premium: { requestsPerMinute: 120, maxKeys: 10, opportunities: 50 } };
  return plans[plan] || plans.free;
}

module.exports = {
  init,
  createUser,
  findUserByEmail,
  findUserById,
  updateUserPlan,
  createApiKey,
  findKeyByRawKey: null,
  getKeysByUserId,
  revokeKey,
  getUserByApiKey,
  comparePassword,
  getPlanLimits,
};
