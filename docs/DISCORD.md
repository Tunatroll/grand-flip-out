# Discord community and bot

## Your Discord server

Use your own Discord server for community, support, and announcements. The website links to a Discord invite so users can join.

### Set your invite link

Replace the placeholder invite in the website with **your** server’s invite:

1. In Discord: Server → Invite people → Create invite. Copy the link (e.g. `https://discord.gg/YourCode`).
2. In this repo, search for `discord.gg/grandflipout` and replace it with your invite (e.g. `discord.gg/YourCode`) in:
   - `website/index.html`
   - `website/pricing.html`
   - `website/support.html`
   - `website/features.html`
   - `website/docs.html`
   - `website/login.html`
   - `website/signup.html`
   - `website/dashboard.html`

So all “Discord” links point to your community.

### Why this is compliant

- Discord is for **your** product community and support, not for game automation or account trading.
- The plugin does not connect to Discord. Users can join Discord voluntarily. No RuneLite or Jagex policy issue.

---

## Optional: Discord bot

A small bot can sit in your server and answer commands (e.g. API status, help, or announcements). The repo includes a scaffold in `server/scripts/discord-bot.js`.

### 1. Create the bot in Discord

1. Go to [Discord Developer Portal](https://discord.com/developers/applications) → New Application (e.g. “Grand Flip Out”).
2. Bot → Add Bot. Copy the **token** (keep it secret).
3. OAuth2 → URL Generator: scopes `bot`, permissions e.g. “Send Messages”, “Read Message History”. Copy the generated URL and open it in a browser to invite the bot to your server.

### 2. Run the bot

```bash
cd server
npm install discord.js   # or add to optionalDependencies
DISCORD_BOT_TOKEN=your_token node scripts/discord-bot.js
```

Set `DISCORD_BOT_TOKEN` in your environment (e.g. in Railway as a separate worker, or on a small VPS). Do not commit the token.

### 3. What the scaffold does

- Logs in and sets status (e.g. “Grand Flip Out API”).
- Listens for messages; you can add commands (e.g. `!status` to ping your API health, `!help` for a short message). See the script for where to add command logic.

### 4. Slash commands (optional)

For slash commands (e.g. `/status`), register them in the Developer Portal and handle them in the bot with the `discord.js` interactions API. The scaffold is a starting point; extend it with your commands.

---

## Summary

- **Invite link:** Replace `discord.gg/grandflipout` with your server invite everywhere in `website/`.
- **Bot (optional):** Create app in Discord, add bot, run `server/scripts/discord-bot.js` with `DISCORD_BOT_TOKEN`. Add commands as needed.

I can’t join your Discord or create the server for you; you add the invite link and run the bot yourself.
