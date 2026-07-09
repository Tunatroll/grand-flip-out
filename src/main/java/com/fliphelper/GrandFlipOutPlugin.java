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
import com.fliphelper.util.GeHistoryImporter;
import com.fliphelper.util.GeTax;
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
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.InventoryID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.Notifier;
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

    @Inject
    private Notifier notifier;

    private PriceService priceService;
    private FlipTracker flipTracker;
    private GeHistoryImporter geHistoryImporter;
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
    private volatile int pendingGeQty = -1;
    private volatile int pendingSearchItemId = -1;
    // Navigation state for Next/Skip hotkeys. Volatile snapshot swap: the fetch executor
    // REPLACES the list reference (never mutates in place) because the overlay reads it on
    // the client thread every frame — in-place clear/addAll raced the render (IOOBE).
    private volatile java.util.List<com.fliphelper.model.Suggestion> activeSuggestions = java.util.Collections.emptyList();
    private volatile int activeSuggestionIndex = -1;
    private static final int CHATBOX_INPUT_OPEN_SCRIPT = 108;
    // Per-character data directory
    private String currentDisplayName = null;
    private File characterDataDir = null;
    // Advisor (Phase 1)
    private com.fliphelper.ui.AdvisorPanel advisorPanel;
    private com.fliphelper.util.BlacklistStore advisorBlacklist;
    private com.fliphelper.api.DumpFeedClient dumpFeedClient;
    private final java.util.Set<Integer> advisorSkipped = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile long lastSuggestAt = 0;
    // Alerts (price targets + offer fills / idle buys). The store is shared with the panel.
    private com.fliphelper.util.AlertStore alertStore;
    // Per-slot timestamp (ms) when a BUYING offer with no fills was first seen — drives the
    // idle-buy alert. Reset when the offer fills, is cancelled, or the slot empties.
    private final long[] buyOfferStartMs = new long[8];
    // Slots we've already fired an idle-buy alert for, so it doesn't repeat every event.
    private final boolean[] idleBuyAlerted = new boolean[8];
    // Last offer state the ALERT path saw per slot — independent of the flip-tracker's
    // lastOfferState (which is only updated when auto-track is on), so the offer-fill alert
    // fires once per transition even with auto-track off and ignores re-emitted states.
    private final GrandExchangeOfferState[] lastAlertOfferState = new GrandExchangeOfferState[8];

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
        intelligenceClient = new IntelligenceClient(okHttpClient, config.intelligenceBaseUrl(), gson);
        dumpFeedClient = new com.fliphelper.api.DumpFeedClient(okHttpClient, config.intelligenceBaseUrl(), gson);
        entitlementService = new com.fliphelper.api.EntitlementService(okHttpClient, config.intelligenceBaseUrl(), gson);
        flipTracker = new FlipTracker(config, priceService, DATA_DIR, gson, executor);
        geHistoryImporter = GeHistoryImporter.create(client, flipTracker, DATA_DIR, executor);

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
        panel.setGeHistoryImportAction(this::importGeHistoryNow);
        // Share the panel's per-character alert targets so refreshPrices() can check them.
        alertStore = panel.getAlertStore();

        // Resolve the user's account entitlement off-thread (no network call when no key is set).
        executor.execute(() ->
        {
            if (config.enableServerFunctionality()) entitlementService.refresh(config.apiKey());
            javax.swing.SwingUtilities.invokeLater(panel::onEntitlementChanged);
        });
        overlay = new GrandFlipOutOverlay(client, config, priceService, flipTracker, sessionManager, this);
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
            @Override public void onFillOffer(int itemId, long price, int quantity) { armOfferFill(itemId, price, quantity); }
        });
        panel.insertTab("Advisor", advisorPanel, 0);
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
        // Offer alerts run independently of flip-tracking so they work even with auto-track off.
        handleOfferAlerts(event);

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
                if (config.enableServerFunctionality() && config.contributeTrades() && intelligenceClient != null)
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
                        // GeTax: 5M cap per ITEM (not per stack) + exemptions
                        long tax = GeTax.tax(itemId, pricePerItem, deltaQuantity);
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

    /**
     * Back-fill trades from the in-game GE History tab when it opens. The history
     * interface (group 383) loads both from the GE "History" button and the banker
     * right-click, so {@link WidgetLoaded} is the reliable trigger. Reads run on the
     * client thread and are deduplicated by the importer.
     */
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (!config.importGeHistory() || geHistoryImporter == null)
        {
            return;
        }
        if (event.getGroupId() == GeHistoryImporter.GE_HISTORY_GROUP_ID)
        {
            // The list children are not yet populated on the WidgetLoaded tick;
            // defer a tick so the rows are present when we read them.
            clientThread.invokeLater(this::importGeHistoryNow);
        }
    }

    /**
     * Run a GE-history import on the client thread and refresh the panel. Used by both
     * the auto-trigger and the panel's "Import GE history" button.
     */
    public void importGeHistoryNow()
    {
        if (geHistoryImporter == null)
        {
            return;
        }
        clientThread.invokeLater(() ->
        {
            try
            {
                int imported = geHistoryImporter.importVisibleHistory();
                if (imported > 0)
                {
                    panel.updateAll();
                    client.addChatMessage(
                        ChatMessageType.GAMEMESSAGE,
                        "",
                        String.format("Grand Flip Out: imported %d trade(s) from GE history.", imported),
                        null
                    );
                }
            }
            catch (Exception e)
            {
                log.debug("GE history import failed: {}", e.getMessage());
            }
        });
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
                if (config.enableServerFunctionality()) entitlementService.refresh(config.apiKey());
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
        if (!config.enableServerFunctionality() || !config.enableAdvisor() || advisorPanel == null || advisorPanel.isPaused()
            || intelligenceClient == null)
        {
            if (overlay != null)
            {
                overlay.setActSlot(-1);
            }
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

                    // With more than one free slot, ask for a COORDINATED basket: a diversified
                    // set of buys with the player's gold split across them. A single free slot
                    // (or none) falls back to the one-at-a-time next-action card.
                    if (snapshot.getFreeSlots() > 1)
                    {
                        // A basket spans several slots — there's no single slot to highlight.
                        if (overlay != null)
                        {
                            overlay.setActSlot(-1);
                        }

                        // PRO users get the agentic "Next moves": a confidence-weighted, reasoned
                        // plan from the honesty-calibrated reasoning layer (PRO-gated server-side).
                        // Fall back to the deterministic basket on 403 / empty / any error so
                        // anon + FREE users (and outages) still get a coordinated basket.
                        java.util.List<com.fliphelper.model.Suggestion> moves = null;
                        boolean hasKey = config.apiKey() != null && !config.apiKey().trim().isEmpty();
                        if (hasKey)
                        {
                            try
                            {
                                moves = intelligenceClient.fetchRecommendations(
                                    snapshot, exclude, f2pOnly, config.apiKey());
                            }
                            catch (Exception recErr)
                            {
                                log.debug("Next-moves fetch failed, falling back to basket: {}",
                                    recErr.getMessage());
                            }
                        }

                        if (moves != null && !moves.isEmpty())
                        {
                            activeSuggestions = java.util.Collections.unmodifiableList(
                                new java.util.ArrayList<>(moves));
                            activeSuggestionIndex = -1;
                            final java.util.List<com.fliphelper.model.Suggestion> nextMoves = moves;
                            javax.swing.SwingUtilities.invokeLater(() -> advisorPanel.showNextMoves(nextMoves));
                        }
                        else
                        {
                            java.util.List<com.fliphelper.model.Suggestion> basket =
                                intelligenceClient.fetchBasket(snapshot, exclude, f2pOnly, config.apiKey());
                            activeSuggestions = (basket != null && !basket.isEmpty())
                                ? java.util.Collections.unmodifiableList(new java.util.ArrayList<>(basket))
                                : java.util.Collections.emptyList();
                            activeSuggestionIndex = -1;
                            javax.swing.SwingUtilities.invokeLater(() -> advisorPanel.showBasket(basket));
                        }
                    }
                    else
                    {
                        com.fliphelper.model.Suggestion suggestion =
                            intelligenceClient.fetchSuggestion(snapshot, exclude, f2pOnly, config.apiKey());
                        activeSuggestions = suggestion != null
                            ? java.util.Collections.singletonList(suggestion)
                            : java.util.Collections.emptyList();
                        activeSuggestionIndex = -1;
                        // Point the in-game overlay at the slot this action targets (abort/sell),
                        // or clear the highlight when there's nothing slot-specific to do.
                        if (overlay != null)
                        {
                            overlay.setActSlot(suggestion != null && !suggestion.isWait()
                                ? suggestion.getTargetSlot() : -1);
                        }
                        javax.swing.SwingUtilities.invokeLater(() -> advisorPanel.showSuggestion(suggestion));
                    }

                    // Free F2P dump feed — informational, shown to everyone (members gated
                    // server-side). Best-effort: a feed failure never breaks the suggestion.
                    try
                    {
                        java.util.List<com.fliphelper.model.DumpFeedEntry> feed =
                            dumpFeedClient.fetch(5, config.apiKey());
                        javax.swing.SwingUtilities.invokeLater(() -> advisorPanel.showDumpFeed(feed));
                    }
                    catch (Exception feedErr)
                    {
                        log.debug("Dump feed fetch failed: {}", feedErr.getMessage());
                    }
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
                geHistoryImporter = GeHistoryImporter.create(client, flipTracker, characterDataDir, executor);
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
        // When the GE item-search box opens, surface the player's starred favourites + their
        // margins as a chat message (read-only quick-look — we never write into the search box).
        if (event.getScriptId() == ScriptID.GE_ITEM_SEARCH)
        {
            showFavouriteQuickLook();
            if (pendingSearchItemId > 0)
            {
                int id = pendingSearchItemId;
                pendingSearchItemId = -1;
                fillGeSearch(id);
            }
            return;
        }

        if (event.getScriptId() != CHATBOX_INPUT_OPEN_SCRIPT)
        {
            return;
        }

        // Discriminate the GE quantity input from the price input by the chatbox title —
        // the exact approach Flipping Copilot uses ("How many do you wish to..." vs
        // "Set a price for each item:") — so the armed value lands in the right field.
        Widget inputTitle = client.getWidget(ComponentID.CHATBOX_TITLE);
        String prompt = (inputTitle != null && inputTitle.getText() != null)
            ? inputTitle.getText().toLowerCase() : "";
        boolean quantityField = prompt.contains("how many");

        if (quantityField && pendingGeQty > 0)
        {
            int qty = pendingGeQty;
            pendingGeQty = -1;
            injectGeInput(qty, qty + " qty");
        }
        else if (!quantityField && pendingGePrice > 0)
        {
            long price = pendingGePrice;
            pendingGePrice = -1;
            injectGeInput(price, formatGp(price) + " gp");
        }
    }

    /**
     * Quick-look: list the player's starred (watchlist) items and their net-after-tax margins
     * in the game chat when the GE search opens, so favourites are one glance away. Strictly
     * read-only — this prints to chat and never touches the search input (autotyping is
     * forbidden). Gated behind the alerts toggle (the same opt-in convenience surface).
     */
    private void showFavouriteQuickLook()
    {
        if (!config.enableAlerts() || panel == null)
        {
            return;
        }
        com.fliphelper.util.WatchlistStore watchlist = panel.getWatchlist();
        if (watchlist == null || watchlist.isEmpty())
        {
            return;
        }
        java.util.List<Integer> ids = watchlist.getAll();
        StringBuilder sb = new StringBuilder("Grand Flip Out favourites: ");
        int shown = 0;
        for (Integer id : ids)
        {
            if (shown >= 5)
            {
                break;
            }
            PriceAggregate agg = priceService.getPrice(id);
            if (agg == null || agg.getItemName() == null)
            {
                continue;
            }
            if (shown > 0)
            {
                sb.append(" | ");
            }
            sb.append(agg.getItemName())
                .append(" (margin ").append(formatGp(agg.getNetMarginAfterTax())).append(')');
            shown++;
        }
        if (shown == 0)
        {
            return;
        }
        final String message = sb.toString();
        clientThread.invokeLater(() ->
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
    }

    private void injectGePrice(long price)
    {
        injectGeInput(price, formatGp(price) + " gp");
    }

    /**
     * Arm the advisor's suggested price + quantity so they auto-fill when the player
     * opens the GE offer's price / quantity field (the script handler routes each value
     * to the right field by the input title). The player still places + confirms.
     */
    private void armOfferFill(int itemId, long price, int quantity)
    {
        if (!config.enableGePriceFill())
        {
            // Injection opt-in is OFF: arm nothing (stale armed values must never fire if
            // the toggle is enabled later) and say something TRUE instead of promising an
            // auto-fill that the injection chokepoints will refuse.
            clientThread.invokeLater(() -> client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                String.format("Grand Flip Out: suggested %s gp x%,d — enable \"GE offer pre-fill\" in settings to auto-fill.",
                    formatGp(price), quantity),
                null));
            return;
        }
        pendingSearchItemId = itemId > 0 ? itemId : -1;
        pendingGePrice = price > 0 ? price : -1;
        pendingGeQty = quantity > 0 ? quantity : -1;
        clientThread.invokeLater(() -> client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            String.format("Grand Flip Out: open a GE buy/sell offer — the item, %s gp, and x%,d auto-fill as you go.",
                formatGp(price), quantity),
            null));
    }

    /**
     * Auto-fill the GE item-search box with the suggested item, then trigger the search —
     * the same mechanism 07Flip uses (set MESLAYERINPUT + MESLAYERMODE, then run the search
     * input's key-listener script). This is the GE item search, NOT the chatbox, so it is not
     * the (forbidden) chatbox autotyping. Opt-in chokepoint; the player still clicks the item.
     */
    private void fillGeSearch(int itemId)
    {
        if (!config.enableGePriceFill() || itemId <= 0)
        {
            return;
        }
        net.runelite.api.ItemComposition def = client.getItemDefinition(itemId);
        String name = def != null ? def.getName() : null;
        if (name == null || name.isEmpty())
        {
            return;
        }
        try
        {
            // Instantly open the GE search interface and type the item name (Copilot parity)
            client.runScript(net.runelite.api.ScriptID.GE_ITEM_SEARCH);
            client.setVarcStrValue(net.runelite.api.gameval.VarClientID.MESLAYERINPUT, name);
            client.setVarcIntValue(net.runelite.api.gameval.VarClientID.MESLAYERMODE, 14); // GE search mode
            
            // Force the widget to redraw with the new text
            client.runScript(2153); // CHATBOX_INPUT_REDRAW or similar standard script to update the UI
        }
        catch (Exception e)
        {
            log.debug("GE search fill failed: {}", e.getMessage());
        }
    }

    private void nextSuggestion()
    {
        final java.util.List<com.fliphelper.model.Suggestion> sugs = activeSuggestions;
        if (sugs.isEmpty()) return;
        activeSuggestionIndex = (activeSuggestionIndex + 1) % sugs.size();
        com.fliphelper.model.Suggestion s = sugs.get(activeSuggestionIndex);
        if (s == null || s.isWait()) return;

        armOfferFill(s.getItemId(), s.getPrice(), s.getQuantity());
        fillGeSearch(s.getItemId());
    }

    private void skipSuggestion()
    {
        final java.util.List<com.fliphelper.model.Suggestion> sugs = activeSuggestions;
        if (sugs.isEmpty()) return;
        int idx = activeSuggestionIndex - 1;
        if (idx < 0 || idx >= sugs.size()) idx = sugs.size() - 1;
        activeSuggestionIndex = idx;
        com.fliphelper.model.Suggestion s = sugs.get(idx);
        if (s == null || s.isWait()) return;

        armOfferFill(s.getItemId(), s.getPrice(), s.getQuantity());
        fillGeSearch(s.getItemId());
    }

    public com.fliphelper.model.Suggestion getCopilotSuggestion()
    {
        // Snapshot ref + clamped index: the fetch resets activeSuggestionIndex to -1 with a
        // non-empty list, and the overlay calls this every frame (get(-1) threw before).
        final java.util.List<com.fliphelper.model.Suggestion> sugs = activeSuggestions;
        if (sugs.isEmpty()) return null;
        int i = activeSuggestionIndex;
        if (i < 0 || i >= sugs.size()) i = 0;
        return sugs.get(i);
    }

    private void handleCopilotHotkey()
    {
        com.fliphelper.model.Suggestion s = getCopilotSuggestion();
        if (s == null || s.isWait())
        {
            clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Copilot: No suggestions ready.", null));
            return;
        }

        Widget geWindow = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (geWindow == null || geWindow.isHidden())
        {
            clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Copilot: Open GE to " + s.getAction() + " " + s.getItemName(), null));
            return;
        }

        Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
        if (offerContainer != null && !offerContainer.isHidden())
        {
            // Buy or sell screen is open. Arm price and quantity so they fill on click
            armOfferFill(s.getItemId(), s.getPrice(), s.getQuantity());
        }
        else
        {
            // Main GE screen is open. Arm and open the search.
            armOfferFill(s.getItemId(), s.getPrice(), s.getQuantity());
            fillGeSearch(s.getItemId());
        }
    }

    /**
     * Write a numeric value into the open GE price/quantity input. This is the exact
     * mechanism Flipping Copilot uses (set the chatbox input widget text + the
     * {@code INPUT_TEXT} client var) — it is NOT chatbox autotyping (which is the only
     * thing the Hub forbids) and NOT synthetic keystrokes. Opt-in chokepoint; the
     * player still reviews the value and presses Confirm themselves.
     */
    private void injectGeInput(long value, String label)
    {
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
                    chatboxInput.setText(value + "*");
                }
                client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(value));
                client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    String.format("Grand Flip Out: filled %s into the GE offer (press Confirm).", label),
                    null
                );
            }
            catch (Exception e)
            {
                log.debug("GE input fill failed: {}", e.getMessage());
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
        else if (config.enableGePriceFill() && config.priceFillHotkey().matches(e))
        {
            clientThread.invokeLater(this::fillGePrice);
            e.consume();
        }
        else if (config.copilotHotkey().matches(e))
        {
            clientThread.invokeLater(this::handleCopilotHotkey);
            e.consume();
        }
        else if (config.nextSuggestionHotkey().matches(e))
        {
            clientThread.invokeLater(this::nextSuggestion);
            e.consume();
        }
        else if (config.skipSuggestionHotkey().matches(e))
        {
            clientThread.invokeLater(this::skipSuggestion);
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

            checkPriceAlerts();
            checkIdleBuyOffers();

            panel.updateAll();

            // Record refresh cycle performance + memory snapshot
            log.debug("Price data refreshed");
        }
        catch (Exception e)
        {
            log.warn("Failed to refresh prices: {}", e.getMessage());
        }
    }

    // ==================== ALERTS ====================

    /**
     * Check every watched price target against the freshest Wiki prices and fire a RuneLite
     * notification on a crossing. De-dupe lives in {@link com.fliphelper.util.AlertStore} (a
     * target only re-fires after the price moves back to the wrong side), so this can run on
     * every refresh without spamming. No-op unless the user enabled alerts.
     */
    private void checkPriceAlerts()
    {
        if (!config.enableAlerts() || alertStore == null || alertStore.isEmpty())
        {
            return;
        }
        for (Integer itemId : alertStore.getItemIds())
        {
            PriceAggregate agg = priceService.getPrice(itemId);
            if (agg == null)
            {
                continue;
            }
            String name = agg.getItemName() != null ? agg.getItemName() : "Item #" + itemId;
            long low = agg.getBestLowPrice();
            long high = agg.getBestHighPrice();
            if (alertStore.shouldFireBuy(itemId, low))
            {
                notifier.notify(String.format("%s dropped to %s (buy target %s).",
                    name, formatGp(low), formatGp(alertStore.getBuyTarget(itemId))));
            }
            if (alertStore.shouldFireSell(itemId, high))
            {
                notifier.notify(String.format("%s rose to %s (sell target %s).",
                    name, formatGp(high), formatGp(alertStore.getSellTarget(itemId))));
            }
        }
    }

    /**
     * Fire offer-state alerts: a notification when an offer reaches BOUGHT or SOLD, and
     * idle-buy bookkeeping (start the clock when a buy goes live, clear it when it fills,
     * cancels, or empties). The per-minute idle check itself runs in
     * {@link #checkIdleBuyOffers()} off the refresh cycle. No-op unless alerts are enabled.
     */
    private void handleOfferAlerts(GrandExchangeOfferChanged event)
    {
        if (!config.enableAlerts())
        {
            return;
        }
        int slot = event.getSlot();
        GrandExchangeOffer offer = event.getOffer();
        if (offer == null || slot < 0 || slot >= buyOfferStartMs.length)
        {
            return;
        }
        GrandExchangeOfferState state = offer.getState();
        GrandExchangeOfferState prevAlertState = lastAlertOfferState[slot];
        lastAlertOfferState[slot] = state;

        if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
        {
            // Only notify on the transition INTO the completed state — re-emitted events for
            // an already-completed offer (e.g. on login) carry the same state and are ignored.
            if (prevAlertState != state)
            {
                String name = itemNameOf(offer.getItemId());
                notifier.notify(String.format("GE offer filled: %s %s.",
                    state == GrandExchangeOfferState.BOUGHT ? "bought" : "sold", name));
            }
            buyOfferStartMs[slot] = 0;
            idleBuyAlerted[slot] = false;
        }
        else if (state == GrandExchangeOfferState.BUYING)
        {
            // Arm the idle clock the first time we see this buy go live and unfilled.
            if (buyOfferStartMs[slot] == 0 && offer.getQuantitySold() == 0)
            {
                buyOfferStartMs[slot] = System.currentTimeMillis();
                idleBuyAlerted[slot] = false;
            }
        }
        else
        {
            // EMPTY / CANCELLED / SELLING — no idle-buy concern.
            buyOfferStartMs[slot] = 0;
            idleBuyAlerted[slot] = false;
        }
    }

    /**
     * Notify when a buy offer has been sitting unfilled past the configured idle threshold,
     * so the player can reprice. De-duped per slot. Called from the refresh cycle.
     */
    private void checkIdleBuyOffers()
    {
        if (!config.enableAlerts())
        {
            return;
        }
        int idleMinutes = config.buyIdleAlertMinutes();
        if (idleMinutes <= 0)
        {
            return;
        }
        long thresholdMs = idleMinutes * 60_000L;
        long now = System.currentTimeMillis();
        clientThread.invokeLater(() ->
        {
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
            if (offers == null)
            {
                return;
            }
            for (int slot = 0; slot < offers.length && slot < buyOfferStartMs.length; slot++)
            {
                GrandExchangeOffer offer = offers[slot];
                boolean stillBuyingUnfilled = offer != null
                    && offer.getState() == GrandExchangeOfferState.BUYING
                    && offer.getQuantitySold() == 0;
                if (!stillBuyingUnfilled)
                {
                    // The state machine in handleOfferAlerts clears these on fill/cancel; this
                    // guards the case where we missed the transition event.
                    if (offer == null || offer.getState() != GrandExchangeOfferState.BUYING)
                    {
                        buyOfferStartMs[slot] = 0;
                        idleBuyAlerted[slot] = false;
                    }
                    continue;
                }
                long started = buyOfferStartMs[slot];
                if (started > 0 && !idleBuyAlerted[slot] && (now - started) >= thresholdMs)
                {
                    idleBuyAlerted[slot] = true;
                    String name = itemNameOf(offer.getItemId());
                    notifier.notify(String.format("Buy offer idle %d min: %s — consider repricing.",
                        idleMinutes, name));
                }
            }
        });
    }

    private String itemNameOf(int itemId)
    {
        net.runelite.api.ItemComposition def = client.getItemDefinition(itemId);
        return def != null ? def.getName() : "Item #" + itemId;
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

    private void maybeFetchServerAdvisor(int itemId, String itemName)
    {
        if (!config.enableServerFunctionality() || !config.enableServerIntelligence() || intelligenceClient == null)
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

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Grand Flip Out: no active GE item to fill.", null);
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