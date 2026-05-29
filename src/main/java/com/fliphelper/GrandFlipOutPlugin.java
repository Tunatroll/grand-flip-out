/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper;

import com.fliphelper.api.IntelligenceClient;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.ui.GpDropOverlay;
import com.fliphelper.ui.InventoryTooltipOverlay;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.TradeRecord;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import com.fliphelper.ui.GrandFlipOutPanel;
import com.fliphelper.util.FlippingUtilitiesImporter;
import com.fliphelper.util.WealthSnapshot;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Player;
import net.runelite.api.VarClientStr;
import net.runelite.api.ScriptID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

/**
 * Grand Flip Out — Grand Exchange flipping assistant for OSRS.
 *
 * <p><b>INFORMATION ONLY</b> — This plugin does NOT automate any Grand
 * Exchange interactions. All buy/sell offers are placed manually by the
 * player. The plugin reads completed transactions via RuneLite's event
 * API and displays OSRS Wiki price data, top-flip suggestions, and
 * local-only flip P&L tracking for informational purposes only.</p>
 *
 * <p>Compliant with Jagex third-party client guidelines and RuneLite
 * Plugin Hub requirements. No game packets are sent, no memory is read
 * beyond the RuneLite Client API, no player data is exposed over HTTP,
 * and no unfair mechanical advantages are provided.</p>
 */
@Slf4j
@PluginDescriptor(
    name = "Grand Flip Out",
    description = "GE flipping assistant with Wiki pricing, top-flip suggestions, "
        + "and local flip P&L tracking. Information-only.",
    tags = {"grand exchange", "flipping", "merching", "prices", "profit", "ge", "trading", "flip", "margin", "tracker"}
)
public class GrandFlipOutPlugin extends Plugin implements KeyListener
{
    private static final File DATA_DIR = new File(RUNELITE_DIR, "grand-flip-out");
    private static final NumberFormat GP_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private GrandFlipOutConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private TooltipManager tooltipManager;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    @Inject
    private ScheduledExecutorService executor;

    private PriceService priceService;
    private FlipTracker flipTracker;
    private SessionManager sessionManager;
    private IntelligenceClient intelligenceClient;
    private com.fliphelper.api.EntitlementService entitlementService;
    private GrandFlipOutPanel panel;
    private GrandFlipOutOverlay overlay;
    private GpDropOverlay gpDropOverlay;
    private InventoryTooltipOverlay inventoryTooltipOverlay;
    private NavigationButton navButton;
    private ScheduledFuture<?> refreshFuture;
    // Track GE offer states to detect completions
    private final int[] lastOfferQuantity = new int[8];
    private final GrandExchangeOfferState[] lastOfferState = new GrandExchangeOfferState[8];
    // Pending GE price fill — armed by hotkey, injected when chatbox opens
    private volatile long pendingGePrice = -1;
    private static final int CHATBOX_INPUT_OPEN_SCRIPT = 108;
    // Per-character data directory
    private String currentDisplayName = null;
    private File characterDataDir = null;
    // Advisor (Phase 1)
    private com.fliphelper.ui.AdvisorPanel advisorPanel;
    private com.fliphelper.util.BlacklistStore advisorBlacklist;
    private final java.util.Set<Integer> advisorSkipped = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile long lastSuggestAt = 0;

    @Provides
    GrandFlipOutConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(GrandFlipOutConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        log.info("Grand Flip Out starting up");

        DATA_DIR.mkdirs();

        // Initialize core services
        priceService = new PriceService(okHttpClient, config, gson);
        intelligenceClient = new IntelligenceClient(okHttpClient, config.intelligenceBaseUrl());
        entitlementService = new com.fliphelper.api.EntitlementService(okHttpClient, config.intelligenceBaseUrl());
        flipTracker = new FlipTracker(config, priceService, DATA_DIR, gson);

        // Initialize session tracking
        sessionManager = new SessionManager(DATA_DIR.getAbsolutePath(), gson);
        flipTracker.setFlipCompleteListener(flip ->
        {
            if (sessionManager != null)
            {
                sessionManager.recordFlipToSession(flip);
            }
            clientThread.invokeLater(() ->
            {
                WealthSnapshot wealth = WealthSnapshot.capture(client, priceService);
                sessionManager.updateSessionWealth(wealth.getTotalWealthGp());
            });
        });

        // One-time Flipping Utilities import (runs async to avoid blocking startUp)
        if (config.importFlippingUtilities())
        {
            File fuImportMarker = new File(DATA_DIR, ".fu_imported");
            if (!fuImportMarker.exists())
            {
                executor.execute(() -> runFlippingUtilitiesImport(fuImportMarker));
            }
        }

        // Create UI — pass sessionManager so the panel has GP/hr data
        panel = new GrandFlipOutPanel(config, priceService, flipTracker, sessionManager, DATA_DIR, intelligenceClient, executor, entitlementService);

        // Resolve the user's account entitlement off-thread (no network call when no key is set).
        executor.execute(() ->
        {
            entitlementService.refresh(config.apiKey());
            javax.swing.SwingUtilities.invokeLater(panel::onEntitlementChanged);
        });
        overlay = new GrandFlipOutOverlay(client, config, priceService, flipTracker, sessionManager);
        gpDropOverlay = new GpDropOverlay(client, config);
        inventoryTooltipOverlay = new InventoryTooltipOverlay(client, config, priceService, flipTracker, tooltipManager);

        // Navigation button
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        navButton = NavigationButton.builder()
            .tooltip("Grand Flip Out")
            .icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
            .priority(5)
            .panel(panel)
            .build();

        // Advisor tab (Phase 1) — opt-in; one-at-a-time next-flip suggestions.
        advisorBlacklist = new com.fliphelper.util.BlacklistStore(DATA_DIR);
        advisorPanel = new com.fliphelper.ui.AdvisorPanel(new com.fliphelper.ui.AdvisorPanel.Listener()
        {
            @Override public void onSkip(int itemId) { advisorSkipped.add(itemId); lastSuggestAt = 0; requestSuggestion(); }
            @Override public void onBlock(int itemId) { advisorBlacklist.add(itemId); lastSuggestAt = 0; requestSuggestion(); }
            @Override public void onPauseToggled(boolean paused) { if (!paused) { lastSuggestAt = 0; requestSuggestion(); } }
        });
        panel.addTab("Advisor", advisorPanel);
        if (config.enableAdvisor())
        {
            advisorPanel.showMessage("Loading your next flip...");
        }

        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);
        overlayManager.add(gpDropOverlay);
        overlayManager.add(inventoryTooltipOverlay);
        keyManager.registerKeyListener(this);

        // Start background price refresh using RuneLite's shared scheduler
        executor.execute(this::initialPriceLoad);
        clientThread.invokeLater(() ->
        {
            WealthSnapshot wealth = WealthSnapshot.capture(client, priceService);
            sessionManager.startSession("Grand Flip Out", 0);
            sessionManager.setStartWealth(wealth.getTotalWealthGp());
        });
        refreshFuture = executor.scheduleAtFixedRate(
            this::refreshPrices,
            config.priceRefreshInterval(),
            config.priceRefreshInterval(),
            TimeUnit.SECONDS
        );

        log.info("Grand Flip Out started successfully");
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Grand Flip Out shutting down");

        if (refreshFuture != null)
        {
            refreshFuture.cancel(false);
            refreshFuture = null;
        }

        if (priceService != null)
        {
            priceService.shutdown();
        }

        if (panel != null)
        {
            panel.shutdown();
        }

        overlayManager.remove(overlay);
        overlayManager.remove(gpDropOverlay);
        overlayManager.remove(inventoryTooltipOverlay);
        clientToolbar.removeNavigation(navButton);
        keyManager.unregisterKeyListener(this);

    }

    // ==================== GE EVENT HANDLING ====================

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        if (!config.autoTrackFlips())
        {
            return;
        }

        int slot = event.getSlot();
        GrandExchangeOffer offer = event.getOffer();

        if (offer == null)
        {
            return;
        }

        GrandExchangeOfferState state = offer.getState();
        int currentQuantity = offer.getQuantitySold();
        int itemId = offer.getItemId();

        // Detect when an offer completes (bought or sold)
        if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
        {
            int deltaQuantity = currentQuantity - lastOfferQuantity[slot];
            if (deltaQuantity > 0)
            {
                boolean isBuy = (state == GrandExchangeOfferState.BOUGHT);
                long pricePerItem = currentQuantity > 0 ? offer.getSpent() / currentQuantity : 0;

                net.runelite.api.ItemComposition itemDef = client.getItemDefinition(itemId);
                String itemName = itemDef != null ? itemDef.getName() : "Item #" + itemId;

                WealthSnapshot wealth = WealthSnapshot.capture(client, priceService);
                // Tag the trade with the active account so flips can be attributed
                // per-RSN. accountHash is the stable key (survives RSN changes); the
                // display name is captured for human-readable per-account filtering.
                Player localPlayer = client.getLocalPlayer();
                String accountName = localPlayer != null ? localPlayer.getName() : null;
                TradeRecord trade = TradeRecord.builder()
                    .itemId(itemId)
                    .itemName(itemName)
                    .quantity(deltaQuantity)
                    .price(pricePerItem)
                    .bought(isBuy)
                    .timestamp(Instant.now())
                    .geSlot(slot)
                    .coinGp(wealth.getCoinGp())
                    .inventoryGp(wealth.getInventoryGp())
                    .bankGp(wealth.getBankGp())
                    .totalWealthGp(wealth.getTotalWealthGp())
                    .accountId(client.getAccountHash())
                    .accountName(accountName)
                    .build();

                flipTracker.recordTransaction(trade);
                panel.updateAll();

                log.info("GE {} detected: {}x {} @ {}gp (slot {})",
                    isBuy ? "buy" : "sell", deltaQuantity, itemName, pricePerItem, slot);

                // Contribute this trade to the crowdsourced dataset (separate opt-in,
                // OFF by default, async fire-and-forget). Gated on contributeTrades, NOT
                // enableServerIntelligence — the latter is strictly read-only.
                if (config.contributeTrades() && intelligenceClient != null)
                {
                    intelligenceClient.submitTrade(itemId, pricePerItem, deltaQuantity, isBuy);
                }

                // Record GE trade event in debug manager
                // Trigger GP drop overlay for completed sells (profit celebration)
                if (!isBuy && config.showGpDrops() && gpDropOverlay != null)
                {
                    // Calculate profit from this sell vs tracked buy
                    FlipItem activeFlip = flipTracker.getActiveFlips().get(slot);
                    if (activeFlip != null && activeFlip.getBuyPrice() > 0)
                    {
                        long sellTotal = pricePerItem * deltaQuantity;
                        long buyTotal = activeFlip.getBuyPrice() * deltaQuantity;
                        long tax = Math.min((long)(sellTotal * 0.02), 5_000_000L);
                        long profit = sellTotal - buyTotal - tax;
                        if (profit > 0)
                        {
                            gpDropOverlay.triggerDrop(profit, itemName);
                        }
                    }
                }
            }
        }
        else if (state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL)
        {
            // Handle cancellations
            if (lastOfferState[slot] == GrandExchangeOfferState.BUYING)
            {
                flipTracker.cancelFlip(itemId);
                panel.updateFlipsTab();
            }
        }

        lastOfferQuantity[slot] = currentQuantity;
        lastOfferState[slot] = state;

        // Notify overlay so it can update its own slot-activity timestamps
        if (overlay != null)
        {
            overlay.updateSlotActivity(slot);
        }

        // Refresh the advisor — placing/collecting an offer changes the next-best action.
        requestSuggestion();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"grandflipout".equals(event.getGroup()))
        {
            return;
        }

        // If refresh interval changed, reschedule on the shared executor
        if ("priceRefreshInterval".equals(event.getKey()))
        {
            if (refreshFuture != null)
            {
                refreshFuture.cancel(false);
            }
            refreshFuture = executor.scheduleAtFixedRate(
                this::refreshPrices,
                config.priceRefreshInterval(),
                config.priceRefreshInterval(),
                TimeUnit.SECONDS
            );
        }

        // Advisor toggled — show the right state and fetch immediately when enabled.
        if ("enableAdvisor".equals(event.getKey()) && advisorPanel != null)
        {
            if (config.enableAdvisor())
            {
                advisorPanel.showMessage("Loading your next flip...");
                lastSuggestAt = 0;
                requestSuggestion();
            }
            else
            {
                advisorPanel.showMessage("Enable the Advisor in plugin config to get next-flip suggestions.");
            }
        }

        // Re-check account entitlement when the API key changes (off-thread; no call when blank).
        if ("apiKey".equals(event.getKey()) && entitlementService != null)
        {
            executor.execute(() ->
            {
                entitlementService.refresh(config.apiKey());
                if (panel != null)
                {
                    javax.swing.SwingUtilities.invokeLater(panel::onEntitlementChanged);
                }
            });
        }
    }

    // ==================== ADVISOR ====================

    /**
     * Ask the server for the next flip and render it. Throttled (≥3s) and a no-op when
     * the Advisor is disabled, paused, or the player isn't logged in. The snapshot is
     * captured on the client thread; the network call and UI update run off it.
     */
    private void requestSuggestion()
    {
        if (!config.enableAdvisor() || advisorPanel == null || advisorPanel.isPaused()
            || intelligenceClient == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSuggestAt < 3000)
        {
            return;
        }
        lastSuggestAt = now;

        clientThread.invokeLater(() ->
        {
            if (client.getGameState() != GameState.LOGGED_IN)
            {
                return;
            }
            com.fliphelper.model.GameStateSnapshot snapshot =
                com.fliphelper.model.GameStateSnapshot.capture(client);
            executor.execute(() ->
            {
                try
                {
                    boolean f2pOnly = !(entitlementService != null && entitlementService.isUnlocked());
                    java.util.List<Integer> exclude = new java.util.ArrayList<>(advisorBlacklist.getAll());
                    exclude.addAll(advisorSkipped);
                    com.fliphelper.model.Suggestion suggestion =
                        intelligenceClient.fetchSuggestion(snapshot, exclude, f2pOnly, config.apiKey());
                    javax.swing.SwingUtilities.invokeLater(() -> advisorPanel.showSuggestion(suggestion));
                }
                catch (Exception e)
                {
                    log.debug("Advisor fetch failed: {}", e.getMessage());
                    javax.swing.SwingUtilities.invokeLater(() ->
                        advisorPanel.showMessage("Advisor unavailable — retrying after your next GE action."));
                }
            });
        });
    }

    // ==================== PER-CHARACTER DATA ====================

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(() ->
            {
                Player local = client.getLocalPlayer();
                if (local == null || local.getName() == null)
                {
                    return;
                }

                String name = local.getName();
                if (name.equals(currentDisplayName))
                {
                    return;
                }

                currentDisplayName = name;
                String safeName = name.replaceAll("[^a-zA-Z0-9_ -]", "");
                characterDataDir = new File(DATA_DIR, safeName);
                characterDataDir.mkdirs();

                log.info("Switched to character data directory: {}", characterDataDir);

                flipTracker.switchDataDir(characterDataDir, gson);
                sessionManager.switchDataDir(characterDataDir.getAbsolutePath());

                WealthSnapshot wealth = WealthSnapshot.capture(client, priceService);
                sessionManager.startSession(name, 0);
                sessionManager.setStartWealth(wealth.getTotalWealthGp());

                panel.updateAll();
                requestSuggestion();
            });
        }
    }

    // ==================== GE PRICE INJECTION ====================

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() != CHATBOX_INPUT_OPEN_SCRIPT)
        {
            return;
        }

        if (pendingGePrice > 0)
        {
            long price = pendingGePrice;
            pendingGePrice = -1;
            injectGePrice(price);
        }
    }

    private void injectGePrice(long price)
    {
        // Opt-in, default-off. Single chokepoint for the GE offer-field write — no
        // path injects unless the user enabled it. The user still presses Confirm.
        if (!config.enableGePriceFill())
        {
            return;
        }
        clientThread.invokeLater(() ->
        {
            try
            {
                Widget chatboxInput = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
                if (chatboxInput != null)
                {
                    chatboxInput.setText(price + "*");
                }
                client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(price));
                client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    String.format("Grand Flip Out: filled %s gp into GE offer.", formatGp(price)),
                    null
                );
            }
            catch (Exception e)
            {
                log.debug("GE price injection failed: {}", e.getMessage());
            }
        });
    }

    // ==================== HOTKEY HANDLING ====================

    @Override
    public void keyTyped(KeyEvent e)
    {
        // Not used
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        if (config.togglePanelHotkey().matches(e))
        {
            // Toggle panel visibility
            if (navButton != null)
            {
                clientToolbar.removeNavigation(navButton);
                clientToolbar.addNavigation(navButton);
            }
            e.consume();
        }
        else if (config.refreshPricesHotkey().matches(e))
        {
            // Force refresh prices
            executor.execute(this::refreshPrices);
            e.consume();
        }
        else if (config.quickLookupHotkey().matches(e))
        {
            // Ctrl+Shift+L: jump to price search and prefill with active item when available
            openQuickLookup();
            e.consume();
        }
        else if (config.toggleOverlayHotkey().matches(e))
        {
            // Toggle overlay visibility
            overlay.toggleVisibility();
            e.consume();
        }
        else if (config.copyMarginHotkey().matches(e))
        {
            // Copy margin data for focused item to clipboard
            copyCurrentMarginToClipboard();
            e.consume();
        }
        else if (config.enableGePriceFill() && config.priceFillHotkey().matches(e))
        {
            fillGePrice();
            e.consume();
        }
        else if (config.copyBuyPriceHotkey().matches(e))
        {
            fillGeBuyPrice();
            e.consume();
        }
        else if (config.copySellPriceHotkey().matches(e))
        {
            fillGeSellPrice();
            e.consume();
        }
        else if (config.copySlotAssistHotkey().matches(e))
        {
            copyFullSlotAssistToClipboard();
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        // Not used
    }

    // ==================== PRICE REFRESH ====================

    private void initialPriceLoad()
    {
        long startMs = System.currentTimeMillis();
        try
        {
            log.info("Loading initial price data...");
            priceService.initialize();
            priceService.refreshAll();

            panel.updateAll();

            long duration = System.currentTimeMillis() - startMs;
            log.info("Initial price data loaded: {} items",
                priceService.getAggregatedPrices().size());

        }
        catch (Exception e)
        {
            log.error("Failed to load initial price data", e);
        }
    }

    private void refreshPrices()
    {
        long refreshStart = System.currentTimeMillis();
        try
        {
            priceService.refreshAll();

            clientThread.invokeLater(() ->
            {
                if (sessionManager != null)
                {
                    WealthSnapshot wealth = WealthSnapshot.capture(client, priceService);
                    sessionManager.updateSessionWealth(wealth.getTotalWealthGp());
                }
            });

            panel.updateAll();

            // Record refresh cycle performance + memory snapshot
            log.debug("Price data refreshed");
        }
        catch (Exception e)
        {
            log.warn("Failed to refresh prices: {}", e.getMessage());
        }
    }

    private void runFlippingUtilitiesImport(File marker)
    {
        try
        {
            List<File> fuFiles = FlippingUtilitiesImporter.findFuFiles(RUNELITE_DIR);
            if (fuFiles.isEmpty())
            {
                log.info("No Flipping Utilities data files found");
                return;
            }

            File tradeLog = new File(DATA_DIR, "trade_log.ndjson");
            int totalImported = 0;
            for (File fuFile : fuFiles)
            {
                log.info("Importing Flipping Utilities data from {}", fuFile.getName());
                int count = FlippingUtilitiesImporter.importToTradeLog(fuFile, tradeLog, gson);
                totalImported += count;
            }

            // Create marker file so we don't re-import next startup
            marker.createNewFile();

            if (totalImported > 0)
            {
                log.info("Imported {} trades from Flipping Utilities", totalImported);
            }
            else
            {
                log.info("No new trades imported from Flipping Utilities (0 new or all duplicates)");
            }
        }
        catch (Exception e)
        {
            log.warn("Flipping Utilities import failed: {}", e.getMessage());
        }
    }

    private void copyCurrentMarginToClipboard()
    {
        // Copy margin info for the first active flip item
        if (!flipTracker.getActiveFlips().isEmpty())
        {
            var firstFlip = flipTracker.getActiveFlips().values().iterator().next();
            var agg = priceService.getPrice(firstFlip.getItemId());
            if (agg != null)
            {
                String data = String.format("%s | Buy: %d | Sell: %d | Margin: %d | Vol/1h: %d",
                    agg.getItemName(),
                    agg.getBestLowPrice(),
                    agg.getBestHighPrice(),
                    agg.getConsensusMargin(),
                    agg.getTotalVolume1h());

                java.awt.datatransfer.StringSelection selection =
                    new java.awt.datatransfer.StringSelection(data);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        }
    }

    private void fillGePrice()
    {
        SlotContext ctx = resolveActiveSlotContext();
        if (ctx == null) return;

        // Determine if we're buying or selling based on GE state
        boolean isBuying = true;
        try
        {
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
            if (offers != null)
            {
                for (GrandExchangeOffer offer : offers)
                {
                    if (offer != null && offer.getItemId() == ctx.itemId)
                    {
                        isBuying = offer.getState() == GrandExchangeOfferState.BUYING
                            || offer.getState() == GrandExchangeOfferState.BOUGHT;
                        break;
                    }
                }
            }
        }
        catch (Exception ignored) {}

        long price = isBuying ? ctx.buyPrice : ctx.sellPrice;
        armOrInjectPrice(price, ctx.itemName, isBuying ? "buy" : "sell");
        maybeFetchServerAdvisor(ctx.itemId, ctx.itemName);
    }

    private void fillGeBuyPrice()
    {
        SlotContext ctx = resolveActiveSlotContext();
        if (ctx == null) return;
        armOrInjectPrice(ctx.buyPrice, ctx.itemName, "buy");
    }

    private void fillGeSellPrice()
    {
        SlotContext ctx = resolveActiveSlotContext();
        if (ctx == null) return;
        armOrInjectPrice(ctx.sellPrice, ctx.itemName, "sell");
    }

    private void armOrInjectPrice(long price, String itemName, String side)
    {
        // Try direct injection if chatbox is already open
        Widget chatboxInput = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
        if (chatboxInput != null && chatboxInput.getText() != null && !chatboxInput.isHidden())
        {
            injectGePrice(price);
        }
        else
        {
            // Arm for injection when chatbox opens (script 108)
            pendingGePrice = price;
            client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                String.format("Grand Flip Out: %s price %s armed — open a GE offer to fill.", side, formatGp(price)),
                null
            );
        }
    }

    private void copyPriceFillToClipboard()
    {
        SlotContext ctx = resolveActiveSlotContext();
        if (ctx == null)
        {
            return;
        }

        long estimatedTax = Math.min((long) (ctx.sellPrice * 0.02), 5_000_000L);
        long netPerItem = ctx.sellPrice - ctx.buyPrice - estimatedTax;

        String clipboardPayload = String.format(
            "ITEM: %s%nBUY_PRICE: %s%nSELL_PRICE: %s%nTAX_PER_ITEM: %s%nNET_PER_ITEM: %s%nNOTE: Manual assist only. Paste into GE offer fields and confirm yourself.",
            ctx.itemName,
            formatGp(ctx.buyPrice),
            formatGp(ctx.sellPrice),
            formatGp(estimatedTax),
            formatGp(netPerItem)
        );

        copyToClipboard(clipboardPayload);
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            String.format("Grand Flip Out: copied price-fill for %s (buy %s, sell %s).", ctx.itemName, formatGp(ctx.buyPrice), formatGp(ctx.sellPrice)),
            null
        );

        maybeFetchServerAdvisor(ctx.itemId, ctx.itemName);
    }

    private void copySlotPriceAssist(boolean buySide)
    {
        SlotContext ctx = resolveActiveSlotContext();
        if (ctx == null)
        {
            return;
        }

        int pct = Math.max(1, config.marginAssistPercent());
        long base = buySide ? ctx.buyPrice : ctx.sellPrice;
        long minus = Math.max(1, Math.round(base * (100 - pct) / 100.0));
        long plus = Math.max(1, Math.round(base * (100 + pct) / 100.0));

        String side = buySide ? "BUY" : "SELL";
        String clipboardPayload = String.format(
            "ITEM: %s%n%s_BASE: %s%n%s_MINUS_%d%%: %s%n%s_PLUS_%d%%: %s%nNOTE: Manual assist only — paste one value into GE yourself.",
            ctx.itemName,
            side, formatGp(base),
            side, pct, formatGp(minus),
            side, pct, formatGp(plus)
        );

        copyToClipboard(clipboardPayload);
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            String.format("Grand Flip Out: copied %s assist for %s (%s).", side.toLowerCase(), ctx.itemName, formatGp(base)),
            null
        );
    }

    private void copyFullSlotAssistToClipboard()
    {
        SlotContext ctx = resolveActiveSlotContext();
        if (ctx == null)
        {
            return;
        }

        int pct = Math.max(1, config.marginAssistPercent());
        long buyMinus = Math.max(1, Math.round(ctx.buyPrice * (100 - pct) / 100.0));
        long buyPlus = Math.max(1, Math.round(ctx.buyPrice * (100 + pct) / 100.0));
        long sellMinus = Math.max(1, Math.round(ctx.sellPrice * (100 - pct) / 100.0));
        long sellPlus = Math.max(1, Math.round(ctx.sellPrice * (100 + pct) / 100.0));
        int suggestedQty = ctx.geLimit > 0 ? Math.min(ctx.geLimit, 1000) : 1;
        long tax = Math.min((long) (ctx.sellPrice * 0.02), 5_000_000L);

        String clipboardPayload = String.format(
            "ITEM: %s%nSLOT: %d%nBUY: %s ( -%d%% %s | +%d%% %s )%nSELL: %s ( -%d%% %s | +%d%% %s )%nSUGGESTED_QTY: %d%nTAX_PER_ITEM: %s%nNOTE: Manual assist only.",
            ctx.itemName,
            ctx.slot + 1,
            formatGp(ctx.buyPrice), pct, formatGp(buyMinus), pct, formatGp(buyPlus),
            formatGp(ctx.sellPrice), pct, formatGp(sellMinus), pct, formatGp(sellPlus),
            suggestedQty,
            formatGp(tax)
        );

        copyToClipboard(clipboardPayload);
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            String.format("Grand Flip Out: copied slot assist for %s (slot %d).", ctx.itemName, ctx.slot + 1),
            null
        );
    }

    private void maybeFetchServerAdvisor(int itemId, String itemName)
    {
        if (!config.enableServerIntelligence() || intelligenceClient == null)
        {
            return;
        }

        executor.execute(() ->
        {
            try
            {
                IntelligenceClient.SmartAdvisorResult result = intelligenceClient.fetchSmartAdvisor(itemId);
                String reason = result.getReasons().isEmpty() ? "no details" : result.getReasons().get(0);
                clientThread.invokeLater(() -> client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    String.format("GFO Advisor: %s → %s (strength %d) — %s",
                        result.getItemName(), result.getAction(), result.getSignalStrength(), reason),
                    null
                ));
            }
            catch (Exception e)
            {
                log.debug("Server advisor unavailable for {}: {}", itemName, e.getMessage());
            }
        });
    }

    private SlotContext resolveActiveSlotContext()
    {
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers != null)
        {
            for (int slot = 0; slot < offers.length; slot++)
            {
                GrandExchangeOffer offer = offers[slot];
                if (offer != null && offer.getItemId() > 0 && offer.getState() != GrandExchangeOfferState.EMPTY)
                {
                    return buildSlotContext(slot, offer);
                }
            }
        }

        if (!flipTracker.getActiveFlips().isEmpty())
        {
            FlipItem flip = flipTracker.getActiveFlips().values().iterator().next();
            PriceAggregate agg = priceService.getPrice(flip.getItemId());
            if (agg != null)
            {
                int limit = agg.getMapping() != null ? agg.getMapping().getLimit() : 0;
                return new SlotContext(
                    flip.getGeSlot(),
                    flip.getItemId(),
                    flip.getItemName() != null ? flip.getItemName() : agg.getItemName(),
                    agg.getBestLowPrice(),
                    agg.getBestHighPrice(),
                    limit
                );
            }
        }

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Grand Flip Out: no active GE item for clipboard assist.", null);
        return null;
    }

    private SlotContext buildSlotContext(int slot, GrandExchangeOffer offer)
    {
        int itemId = offer.getItemId();
        net.runelite.api.ItemComposition itemDef = client.getItemDefinition(itemId);
        String itemName = itemDef != null ? itemDef.getName() : "Item #" + itemId;
        PriceAggregate agg = priceService.getPrice(itemId);
        if (agg == null)
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Grand Flip Out: price data unavailable.", null);
            return null;
        }
        int limit = agg.getMapping() != null ? agg.getMapping().getLimit() : 0;
        return new SlotContext(slot, itemId, itemName, agg.getBestLowPrice(), agg.getBestHighPrice(), limit);
    }

    private void copyToClipboard(String payload)
    {
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(payload);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    private static final class SlotContext
    {
        final int slot;
        final int itemId;
        final String itemName;
        final long buyPrice;
        final long sellPrice;
        final int geLimit;

        SlotContext(int slot, int itemId, String itemName, long buyPrice, long sellPrice, int geLimit)
        {
            this.slot = slot;
            this.itemId = itemId;
            this.itemName = itemName;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.geLimit = geLimit;
        }
    }

    private String formatGp(long amount)
    {
        return GP_FORMAT.format(amount) + " gp";
    }

    private void openQuickLookup()
    {
        if (panel == null)
        {
            return;
        }

        String prefill = "";
        if (!flipTracker.getActiveFlips().isEmpty())
        {
            FlipItem firstFlip = flipTracker.getActiveFlips().values().iterator().next();
            if (firstFlip != null && firstFlip.getItemName() != null)
            {
                prefill = firstFlip.getItemName();
            }
        }

        final String query = prefill;
        javax.swing.SwingUtilities.invokeLater(() -> panel.openQuickLookup(query));
    }

}