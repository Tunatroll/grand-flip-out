#!/usr/bin/env node
/**
 * Optional Discord bot for Grand Flip Out community server.
 * Set DISCORD_BOT_TOKEN. Invite the bot to your server via Discord Developer Portal.
 * Add command logic below (e.g. !status, !help).
 */
const token = process.env.DISCORD_BOT_TOKEN;
if (!token) {
  console.error('Set DISCORD_BOT_TOKEN');
  process.exit(1);
}

let Client;
try {
  Client = require('discord.js').Client;
} catch {
  console.error('Install discord.js: npm install discord.js');
  process.exit(1);
}

const client = new Client({ intents: ['Guilds', 'GuildMessages', 'MessageContent'] });

client.once('ready', () => {
  console.log('Discord bot ready:', client.user.tag);
  client.user.setActivity('Grand Flip Out API', { type: 3 }); // Watching
});

client.on('messageCreate', (msg) => {
  if (msg.author.bot) return;
  const text = (msg.content || '').trim().toLowerCase();

  if (text === '!help' || text === '!gfo') {
    msg.reply('Grand Flip Out: RuneLite plugin for GE market data and flip tracking. Get your API key at grandflipout.com → Sign up → Dashboard. Plugin config: Server URL + API Key. No automation—RuneLite compliant.');
    return;
  }

  if (text === '!status') {
    const apiUrl = process.env.API_BASE_URL || 'https://grandflipout.com';
    fetch(apiUrl + '/health').then(r => r.json()).then(d => {
      msg.reply('API: ' + (d.status === 'ok' ? 'OK' : 'Unknown'));
    }).catch(() => {
      msg.reply('API: unreachable');
    });
    return;
  }
});

client.login(token).catch((err) => {
  console.error('Login failed:', err.message);
  process.exit(1);
});
