package com.fliphelper;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.FlipState;
import com.fliphelper.model.TradeRecord;
import com.fliphelper.tracker.*;
import com.fliphelper.ui.GrandFlipOutPanel;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

/**
 * Grand Flip Out — Comprehensive Grand Exchange flipping assistant for OSRS.
 *
 * <p><b>INFORMATION ONLY</b> — This plugin does NOT automate any Grand Exchange
 * interactions. All buy/sell offers are placed manually by the player.
 * The plugin reads completed transactions via RuneLite's event API and
 * displays price data, flip suggestions, dump analysis, and portfolio
 * tracking for informational purposes only.</p>
 *
 * <p>Compliant with Jagex third-party client guidelines and RuneLite
 * Plugin Hub requirements. No game packets are sent, no memory is read
 * beyond the RuneLite Client API, and no unfair mechanical advantages
 * are provided.</p>
 *
 * <p>Multi-account tracking is permitted under Jagex's multi-logging policy
 * (allowed since 2014). However, <b>coordinated market manipulation across
 * accounts is strictly against Jagex Terms of Service</b>. The multi-account
 * features in this plugin are for independent portfolio monitoring only.</p>
 */
@Slf4j
@PluginDescriptor(
    name = "Grand Flip Out",
    description = "Comprehensive GE flipping assistant with multi-source pricing, flip tracking, profit analysis, and smart suggestions",
    tags = {"grand exchange", "flipping", "merching", "prices", "profit", "ge", "trading"}
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

    private PriceService priceService;
    private FlipTracker flipTracker;
    private FlipSuggestionEngine suggestionEngine;
    private DumpDetector dumpDetector;
    private DumpKnowledgeEngine dumpKnowledgeEngine;
    private JagexTradeIndex jagexTradeIndex;
    private MarketIntelligenceEngine marketIntelligence;
    private BotEconomyTracker botEconomyTracker;
    private DataQualityAnalyzer dataQualityAnalyzer;
    private InvestmentHorizonAnalyzer investmentHorizonAnalyzer;
    private MultiAccountDashboard multiAccountDashboard;
    private SessionManager sessionManager;
    private RiskManager riskManager;
    private SlotOptimizer slotOptimizer;
    private MarginCheckTracker marginCheckTracker;
    private SmartAdvisor smartAdvisor;
    private GrandFlipOutPanel panel;
    private GrandFlipOutOverlay overlay;
    private NavigationButton navButton;
    private ScheduledExecutorService executor;
    private int currentAccountIndex = 0;

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
        priceService = new PriceService(okHttpClient, config);
        flipTracker = new FlipTracker(config, DATA_DIR);
        suggestionEngine = new FlipSuggestionEngine(priceService, config);

        // Initialize dump detection, knowledge engine, and market intelligence
        dumpDetector = new DumpDetector(priceService);
        dumpKnowledgeEngine = new DumpKnowledgeEngine();
        jagexTradeIndex = new JagexTradeIndex(priceService);
        marketIntelligence = new MarketIntelligenceEngine(priceService);
        botEconomyTracker = new BotEconomyTracker(priceService);
        dataQualityAnalyzer = new DataQualityAnalyzer(priceService);
        investmentHorizonAnalyzer = new InvestmentHorizonAnalyzer(priceService);

        // Initialize multi-account dashboard (up to 10 accounts)
        multiAccountDashboard = new MultiAccountDashboard(DATA_DIR.getAbsolutePath());
        if (config.enableMultiAccount())
        {
            initializeAccounts();
        }

        // Initialize session, risk, slot, and margin systems
        sessionManager = new SessionManager(DATA_DIR.getAbsolutePath());
        riskManager = new RiskManager();
        slotOptimizer = new SlotOptimizer();
        marginCheckTracker = new MarginCheckTracker(DATA_DIR.getAbsolutePath());

        // Initialize SmartAdvisor — the unified intelligence brain
        smartAdvisor = new SmartAdvisor(
            priceService, marketIntelligence, botEconomyTracker,
            jagexTradeIndex, dumpDetector, dumpKnowledgeEngine,
            dataQualityAnalyzer, riskManager, investmentHorizonAnalyzer,
            priceService.getHistoryCollector()
        );

        // Create UI
        panel = new GrandFlipOutPanel(config, priceService, flipTracker, suggestionEngine, smartAdvisor);
        overlay = new GrandFlipOutOverlay(client, config, priceService, flipTracker);

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
        keyManager.registerKeyListener(this);

        // Start background price refresh
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.execute(this::initialPriceLoad);
        executor.scheduleAtFixedRate(
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

        if (executor != null)
        {
            executor.shutdownNow();
        }

        // Save multi-account data on shutdown
        if (multiAccountDashboard != null)
        {
            multiAccountDashboard.saveToFile();
        }

        overlayManager.remove(overlay);
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
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"grandflipout".equals(event.getGroup()))
        {
            return;
        }

        // If refresh interval changed, restart the scheduler
        if ("priceRefreshInterval".equals(event.getKey()) && executor != null)
        {
            executor.shutdownNow();
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(
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
        else if (config.dumpScanHotkey().matches(e))
        {
            // Ctrl+Shift+D: Immediately scan for dump opportunities
            executor.execute(this::runDumpScan);
            e.consume();
        }
        else if (config.nextAccountHotkey().matches(e))
        {
            // Ctrl+Shift+Tab: Cycle through accounts in multi-account view
            if (config.enableMultiAccount())
            {
                currentAccountIndex = (currentAccountIndex + 1) % config.accountCount();
                log.info("Switched to Account {}", currentAccountIndex + 1);
                panel.updateAll();
            }
            e.consume();
        }
        else if (config.quickBuyPlanHotkey().matches(e))
        {
            // Ctrl+Shift+B: Generate a quick multi-account buy plan
            if (config.enableMultiAccount())
            {
                executor.execute(this::runDumpScan);
            }
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
        try
        {
            log.info("Loading initial price data...");
            priceService.initialize();
            priceService.refreshAll();

            // Seed price history for top items from Wiki timeseries API
            // This bootstraps EMA/RSI/MACD/Bollinger analysis immediately
            log.info("Seeding price history from Wiki timeseries API...");
            priceService.getHistoryCollector().seedTopItems(50);

            panel.updateAll();
            log.info("Initial price data loaded: {} items, history seeded for top 50",
                priceService.getAggregatedPrices().size());
        }
        catch (Exception e)
        {
            log.error("Failed to load initial price data", e);
        }
    }

    private void refreshPrices()
    {
        try
        {
            priceService.refreshAll();

            // Record JTI tick, dump snapshots, and market intelligence on each refresh
            jagexTradeIndex.recordTick();
            dumpDetector.takeSnapshot();

            // Supply chain check — detect supply disruption / volume shock signals
            var marketHealthScore = botEconomyTracker.getMarketHealth();
            if (marketHealthScore != null)
            {
                log.debug("Supply chain: health={}, pipeline={}",
                    marketHealthScore,
                    botEconomyTracker.getPipelineTracker().getPipelineStage());
            }

            // Generate market briefing on each refresh (lightweight)
            var briefing = marketIntelligence.getMarketBriefing();
            if (briefing != null)
            {
                log.debug("Market briefing: volatility={}, topMovers={}",
                    briefing.getVolatilityLevel(),
                    briefing.getTopMovers() != null ? briefing.getTopMovers().size() : 0);
            }

            // Run dump detection if enabled
            if (config.enableDumpDetection())
            {
                var alerts = dumpDetector.detectAnomalies();
                if (!alerts.isEmpty() && config.dumpAutoAnalyze())
                {
                    for (var alert : alerts)
                    {
                        if (alert.getType() == DumpDetector.AlertType.DUMP)
                        {
                            var knowledgeAlert = convertToDumpKnowledgeAlert(alert);
                            var analysis = dumpKnowledgeEngine.analyzeDump(knowledgeAlert);
                            log.info("Dump detected: {} — Action: {}, Confidence: {}",
                                alert.getItemName(),
                                analysis.getRecommendedAction(),
                                analysis.getConfidence());
                        }
                    }
                }
            }

            // Update multi-account positions with latest prices
            if (config.enableMultiAccount())
            {
                updateMultiAccountPrices();
            }

            panel.updateAll();
            log.debug("Price data refreshed");
        }
        catch (Exception e)
        {
            log.warn("Failed to refresh prices: {}", e.getMessage());
        }
    }

    // ==================== MULTI-ACCOUNT HELPERS ====================

    private void initializeAccounts()
    {
        int count = config.accountCount();
        for (int i = 1; i <= count; i++)
        {
            String name = "Account " + i;
            if (multiAccountDashboard.getAccount(name) == null)
            {
                multiAccountDashboard.addAccount(
                    MultiAccountDashboard.Account.builder()
                        .accountName(name)
                        .cashStack(0)
                        .totalBankValue(0)
                        .positions(new java.util.concurrent.ConcurrentHashMap<>())
                        .geLimitResets(new java.util.concurrent.ConcurrentHashMap<>())
                        .totalGeSlotsUsed(0)
                        .isMembers(true)
                        .lastActive(Instant.now())
                        .build()
                );
            }
        }
        log.info("Initialized {} accounts for multi-account tracking", count);
    }

    private void updateMultiAccountPrices()
    {
        for (var account : multiAccountDashboard.getAllAccounts())
        {
            for (var position : account.getPositions().values())
            {
                var agg = priceService.getPrice(position.getItemId());
                if (agg != null)
                {
                    long currentPrice = agg.getBestHighPrice();
                    position.setCurrentPrice(currentPrice);
                    long cost = position.getBuyPrice() * position.getQuantity();
                    long value = currentPrice * position.getQuantity();
                    position.setUnrealizedPnL(value - cost);
                }
            }
        }
    }

    /**
     * Run a dump scan and display analysis results.
     *
     * <p><b>INFORMATION ONLY</b> — No GE actions are automated.
     * The player must manually execute all trades.</p>
     */
    private void runDumpScan()
    {
        try
        {
            var alerts = dumpDetector.detectAnomalies();
            var dumpAlerts = alerts.stream()
                .filter(a -> a.getType() == DumpDetector.AlertType.DUMP)
                .collect(java.util.stream.Collectors.toList());

            if (dumpAlerts.isEmpty())
            {
                log.info("Dump scan: No active dumps detected");
                return;
            }

            log.info("Dump scan: {} dump(s) detected", dumpAlerts.size());

            for (var alert : dumpAlerts)
            {
                var knowledgeAlert = convertToDumpKnowledgeAlert(alert);
                var analysis = dumpKnowledgeEngine.analyzeDump(knowledgeAlert);
                log.info("  {} — {} (confidence: {}, target buy: {}, recovery: {})",
                    alert.getItemName(),
                    analysis.getRecommendedAction(),
                    analysis.getConfidence(),
                    analysis.getTargetBuyPrice(),
                    analysis.getExpectedRecoveryPrice());

                // Log per-account GE limit availability (informational only)
                if (config.enableMultiAccount())
                {
                    var availability = multiAccountDashboard.getGeLimitAvailability(alert.getItemId());
                    log.info("  Per-account GE limit availability for {}: {}", alert.getItemName(), availability);
                }
            }

            panel.updateAll();
        }
        catch (Exception e)
        {
            log.warn("Dump scan failed: {}", e.getMessage());
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

    /**
     * Converts a DumpDetector.PriceAlert to DumpKnowledgeEngine.PriceAlert format
     * for compatibility with the dump knowledge engine analyzer.
     */
    private DumpKnowledgeEngine.PriceAlert convertToDumpKnowledgeAlert(DumpDetector.PriceAlert alert)
    {
        // Get current price aggregate to fill in missing fields
        var agg = priceService.getPrice(alert.getItemId());
        long currentPrice = agg != null ? agg.getCurrentPrice() : 0;
        long previousPrice = agg != null ? (currentPrice - (long)(currentPrice * 0.05)) : currentPrice; // Estimate previous

        return DumpKnowledgeEngine.PriceAlert.builder()
            .itemId(alert.getItemId())
            .itemName(alert.getItemName())
            .currentPrice(currentPrice)
            .previousPrice(previousPrice)
            .volume(agg != null ? agg.getTotalVolume1h() : 0)
            .buyPrice(agg != null ? agg.getBestLowPrice() : currentPrice)
            .sellPrice(agg != null ? agg.getBestHighPrice() : currentPrice)
            .priceMovement(((currentPrice - previousPrice) / (double) Math.max(1, previousPrice)) * 100.0)
            .build();
    }
}
