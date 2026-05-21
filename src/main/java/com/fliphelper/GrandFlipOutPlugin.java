package com.fliphelper;

import com.fliphelper.api.PriceService;
import com.fliphelper.ui.GpDropOverlay;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.TradeRecord;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import com.fliphelper.ui.GrandFlipOutPanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
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
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.List;
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
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    @Inject
    private ScheduledExecutorService executor;

    private PriceService priceService;
    private FlipTracker flipTracker;
    private SessionManager sessionManager;
    private GrandFlipOutPanel panel;
    private GrandFlipOutOverlay overlay;
    private GpDropOverlay gpDropOverlay;
    private NavigationButton navButton;
    private ScheduledFuture<?> refreshFuture;
    // Track GE offer states to detect completions
    private final int[] lastOfferQuantity = new int[8];
    private final GrandExchangeOfferState[] lastOfferState = new GrandExchangeOfferState[8];

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
        flipTracker = new FlipTracker(config, DATA_DIR, gson);

        // Initialize session tracking
        sessionManager = new SessionManager(DATA_DIR.getAbsolutePath(), gson);

        // Create UI — pass sessionManager so ProfitChartPanel has GP/hr data
        panel = new GrandFlipOutPanel(config, priceService, flipTracker, sessionManager);
        overlay = new GrandFlipOutOverlay(client, config, priceService, flipTracker);
        gpDropOverlay = new GpDropOverlay(client, config);

        // Navigation button
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        navButton = NavigationButton.builder()
            .tooltip("Grand Flip Out")
            .icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);
        overlayManager.add(gpDropOverlay);
        keyManager.registerKeyListener(this);

        // Start background price refresh using RuneLite's shared scheduler
        executor.execute(this::initialPriceLoad);
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

        overlayManager.remove(overlay);
        overlayManager.remove(gpDropOverlay);
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

                TradeRecord trade = TradeRecord.builder()
                    .itemId(itemId)
                    .itemName(itemName)
                    .quantity(deltaQuantity)
                    .price(pricePerItem)
                    .bought(isBuy)
                    .timestamp(Instant.now())
                    .geSlot(slot)
                    .build();

                flipTracker.recordTransaction(trade);
                panel.updateAll();

                log.info("GE {} detected: {}x {} @ {}gp (slot {})",
                    isBuy ? "buy" : "sell", deltaQuantity, itemName, pricePerItem, slot);

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
        else if (config.copyBuyPriceHotkey().matches(e))
        {
            executor.execute(() -> copyPriceAssist(true));
            e.consume();
        }
        else if (config.copySellPriceHotkey().matches(e))
        {
            executor.execute(() -> copyPriceAssist(false));
            e.consume();
        }
        else if (config.copySlotAssistHotkey().matches(e))
        {
            executor.execute(this::copySlotAssistBlock);
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

            panel.updateAll();

            // Record refresh cycle performance + memory snapshot
            log.debug("Price data refreshed");
        }
        catch (Exception e)
        {
            log.warn("Failed to refresh prices: {}", e.getMessage());
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

    private void copyPriceAssist(boolean buySide)
    {
        PriceContext ctx = resolvePriceContext();
        if (ctx == null)
        {
            return;
        }

        int pct = Math.max(1, config.marginAssistPercent());
        long base = buySide ? ctx.buyPrice : ctx.sellPrice;
        long lower = Math.max(1, Math.round(base * (100 - pct) / 100.0));
        long upper = Math.max(1, Math.round(base * (100 + pct) / 100.0));
        String side = buySide ? "BUY" : "SELL";

        String payload = String.format(
            "ITEM: %s%n%s_BASE: %d%n%s_MINUS_%d%%: %d%n%s_PLUS_%d%%: %d%nNOTE: Clipboard assist only. Paste and confirm manually in GE.",
            ctx.itemName, side, base, side, pct, lower, side, pct, upper
        );
        copyToClipboard(payload);

        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            String.format("Grand Flip Out: copied %s assist for %s.", side.toLowerCase(), ctx.itemName),
            null
        );
    }

    private void copySlotAssistBlock()
    {
        PriceContext ctx = resolvePriceContext();
        if (ctx == null)
        {
            return;
        }

        int pct = Math.max(1, config.marginAssistPercent());
        long buyLower = Math.max(1, Math.round(ctx.buyPrice * (100 - pct) / 100.0));
        long buyUpper = Math.max(1, Math.round(ctx.buyPrice * (100 + pct) / 100.0));
        long sellLower = Math.max(1, Math.round(ctx.sellPrice * (100 - pct) / 100.0));
        long sellUpper = Math.max(1, Math.round(ctx.sellPrice * (100 + pct) / 100.0));
        long taxPerItem = Math.min((long) (ctx.sellPrice * 0.02), 5_000_000L);
        long netPerItem = ctx.sellPrice - ctx.buyPrice - taxPerItem;
        int qtyHint = ctx.buyLimit > 0 ? Math.min(ctx.buyLimit, 1000) : 1;

        String payload = String.format(
            "ITEM: %s%nBUY: %d (-%d%% %d | +%d%% %d)%nSELL: %d (-%d%% %d | +%d%% %d)%nQTY_HINT: %d%nTAX_PER_ITEM: %d%nNET_PER_ITEM: %d%nNOTE: Manual assist only.",
            ctx.itemName,
            ctx.buyPrice, pct, buyLower, pct, buyUpper,
            ctx.sellPrice, pct, sellLower, pct, sellUpper,
            qtyHint, taxPerItem, netPerItem
        );
        copyToClipboard(payload);

        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            String.format("Grand Flip Out: copied slot assist block for %s.", ctx.itemName),
            null
        );
    }

    private PriceContext resolvePriceContext()
    {
        if (!flipTracker.getActiveFlips().isEmpty())
        {
            FlipItem firstFlip = flipTracker.getActiveFlips().values().iterator().next();
            var agg = priceService.getPrice(firstFlip.getItemId());
            if (agg != null)
            {
                return new PriceContext(
                    agg.getItemName(),
                    agg.getBestLowPrice(),
                    agg.getBestHighPrice(),
                    agg.getBuyLimit()
                );
            }
        }

        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            "Grand Flip Out: no active item found for clipboard assist.",
            null
        );
        return null;
    }

    private void copyToClipboard(String payload)
    {
        java.awt.datatransfer.StringSelection selection =
            new java.awt.datatransfer.StringSelection(payload);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    private static final class PriceContext
    {
        private final String itemName;
        private final long buyPrice;
        private final long sellPrice;
        private final int buyLimit;

        private PriceContext(String itemName, long buyPrice, long sellPrice, int buyLimit)
        {
            this.itemName = itemName;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.buyLimit = buyLimit;
        }
    }

}