package com.fliphelper;

import com.fliphelper.api.BackendClient;
import com.fliphelper.api.MarketSignalClient;
import com.fliphelper.api.PeerNetwork;
import com.fliphelper.api.ProfileClient;
import com.fliphelper.api.PriceService;
import com.fliphelper.ui.ProfilePanel;
import com.fliphelper.ui.DebugPanel;
import com.fliphelper.ui.GePriceHelper;
import com.fliphelper.ui.GpDropOverlay;
import com.fliphelper.debug.DebugManager;
import com.fliphelper.debug.DebugOverlay;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.FlipState;
import com.fliphelper.model.TradeRecord;
import com.fliphelper.tracker.*;
import com.fliphelper.ui.GrandFlipOutPanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
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
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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

    @Inject
    private Gson gson;

    private PriceService priceService;
    private FlipTracker flipTracker;
    private FlipSuggestionEngine suggestionEngine;
    private QuickFlipAnalyzer quickFlipAnalyzer;
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
    private MarketSignalClient marketSignalClient;
    private PeerNetwork peerNetwork;
    private ProfileClient profileClient;
    private BackendClient backendClient;
    private DebugManager debugManager;
    private DebugPanel debugPanel;
    private GePriceHelper gePriceHelper;
    private GrandFlipOutPanel panel;
    private ProfilePanel profilePanel;
    private GrandFlipOutOverlay overlay;
    private DebugOverlay debugOverlay;
    private GpDropOverlay gpDropOverlay;
    private NavigationButton navButton;
    private ScheduledExecutorService executor;
    private int currentAccountIndex = 0;
    private long lastPredictionStatsPollMs = 0L;

    // Track GE offer states to detect completions
    private final int[] lastOfferQuantity = new int[8];
    private final GrandExchangeOfferState[] lastOfferState = new GrandExchangeOfferState[8];
    // Track when each slot last had activity (for slot timer display like Flipping Utilities)
    private final Instant[] slotLastActive = new Instant[8];

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

        // Initialize debug manager
        debugManager = new DebugManager();
        debugManager.info(getClass().getSimpleName(), "Plugin startup initiated");

        // Initialize core services
        priceService = new PriceService(okHttpClient, config, gson);
        priceService.setDebugManager(debugManager);
        flipTracker = new FlipTracker(config, DATA_DIR, gson);
        flipTracker.setDebugManager(debugManager);
        // Wire profile flip logging — when a buy→sell pair completes, log it to the user's profile
        // Routes through ProfileClient → PeerNetwork for P2P failover
        flipTracker.setFlipCompleteListener(flip -> {
            if (profileClient != null && profileClient.isLoggedIn())
            {
                profileClient.logFlip(
                    flip.getItemId(),
                    flip.getItemName(),
                    flip.getBuyPrice(),
                    flip.getSellPrice(),
                    flip.getQuantity(),
                    config.profileCharacterName()
                );
            }
        });
        quickFlipAnalyzer = new QuickFlipAnalyzer();
        suggestionEngine = new FlipSuggestionEngine(priceService, config, quickFlipAnalyzer);

        // Initialize dump detection, knowledge engine, and market intelligence
        dumpDetector = new DumpDetector(priceService);
        dumpKnowledgeEngine = new DumpKnowledgeEngine();
        jagexTradeIndex = new JagexTradeIndex(priceService);
        marketIntelligence = new MarketIntelligenceEngine(priceService);
        botEconomyTracker = new BotEconomyTracker(priceService);
        dataQualityAnalyzer = new DataQualityAnalyzer(priceService);
        investmentHorizonAnalyzer = new InvestmentHorizonAnalyzer(priceService);

        // Initialize multi-account dashboard (up to 10 accounts)
        multiAccountDashboard = new MultiAccountDashboard(DATA_DIR.getAbsolutePath(), gson);
        if (config.enableMultiAccount())
        {
            initializeAccounts();
        }

        // Initialize session, risk, slot, and margin systems
        sessionManager = new SessionManager(DATA_DIR.getAbsolutePath(), gson);
        riskManager = new RiskManager();
        slotOptimizer = new SlotOptimizer();
        marginCheckTracker = new MarginCheckTracker(DATA_DIR.getAbsolutePath(), gson);

        // Initialize SmartAdvisor — the unified intelligence brain
        smartAdvisor = new SmartAdvisor(
            priceService, marketIntelligence, botEconomyTracker,
            jagexTradeIndex, dumpDetector, dumpKnowledgeEngine,
            dataQualityAnalyzer, riskManager, investmentHorizonAnalyzer,
            priceService.getHistoryCollector()
        );

        // Initialize GE price helper for offer auto-fill
        gePriceHelper = new GePriceHelper(client, clientThread, priceService);

        // ── Market Signal Client ──
        marketSignalClient = new MarketSignalClient();
        if (config.enableNeuralNet())
        {
            marketSignalClient.setSignalUrl(config.neuralNetUrl());
            marketSignalClient.setEnabled(true);
            log.info("Market signal engine enabled → {}", config.neuralNetUrl());
        }
        else
        {
            marketSignalClient.setEnabled(false);
        }

        // Create UI — pass sessionManager so ProfitChartPanel has GP/hr data
        panel = new GrandFlipOutPanel(config, priceService, flipTracker, suggestionEngine, smartAdvisor, sessionManager, marketSignalClient);
        // NOTE: Profile tab added below, after profilePanel is initialized
        overlay = new GrandFlipOutOverlay(client, config, priceService, flipTracker);
        debugOverlay = new DebugOverlay(config, priceService, flipTracker, debugManager);
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
        overlayManager.add(debugOverlay);
        overlayManager.add(gpDropOverlay);
        keyManager.registerKeyListener(this);

        // ── P2P Network (fundamental to GFO architecture) ──
        peerNetwork = new PeerNetwork(okHttpClient, gson);
        if (config.enableP2P())
        {
            // Parse additional peers from config (comma-separated)
            List<String> extraPeers = Arrays.stream(config.additionalPeers().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

            // Always add the configured backend URL as a seed peer
            String backendBase = config.backendUrl().replace("/api/contribute", "");
            extraPeers.add(backendBase);

            peerNetwork.start(extraPeers);
            log.info("P2P network started with {} extra seed(s)", extraPeers.size());
        }

        // ── Profile & Account Client (tier gating, account management) ──
        profileClient = new ProfileClient(peerNetwork, gson);
        if (config.profileApiKey() != null && !config.profileApiKey().isEmpty())
        {
            // Auto-login with saved API key
            boolean loggedIn = profileClient.login(config.profileApiKey());
            if (loggedIn)
            {
                log.info("Profile auto-login: {} (tier: {})",
                    profileClient.getDisplayName(), profileClient.getTier());
            }
        }

        // ── Profile Panel (account UI tab) ──
        profilePanel = new ProfilePanel(profileClient, peerNetwork);
        panel.addTab("Profile", profilePanel);  // Added here — after profilePanel is initialized

        // ── Debug Panel (live stats + debug report) ──
        debugPanel = new DebugPanel(debugManager);
        panel.addTab("Debug", debugPanel);

        // ── Crowdsourced BackendClient (batched trade contributions) ──
        backendClient = new BackendClient(okHttpClient, gson);
        backendClient.setDebugManager(debugManager);
        backendClient.setBackendUrl(config.backendUrl());
        backendClient.setPeerNetwork(peerNetwork); // P2P fanout mode
        backendClient.setProfileConfig(
            config.profileApiKey(),
            config.profileCharacterName(),
            config.backendUrl()
        );
        if (config.enableCrowdsourced())
        {
            backendClient.start();
        }

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
            executor.shutdown();
            try
            {
                if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                {
                    executor.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        if (priceService != null)
        {
            priceService.shutdown();
        }

        // Save multi-account data on shutdown
        if (multiAccountDashboard != null)
        {
            multiAccountDashboard.saveToFile();
        }

        // Stop backend client
        if (backendClient != null)
        {
            backendClient.stop();
        }

        // Stop P2P network
        if (peerNetwork != null)
        {
            peerNetwork.stop();
        }

        overlayManager.remove(overlay);
        overlayManager.remove(debugOverlay);
        overlayManager.remove(gpDropOverlay);
        clientToolbar.removeNavigation(navButton);
        keyManager.unregisterKeyListener(this);

        if (debugManager != null)
        {
            debugManager.clearAll();
        }
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

        // Send raw portfolio state to local web UI
        net.runelite.api.ItemComposition itemDef = client.getItemDefinition(itemId);
        String itemName = itemDef != null ? itemDef.getName() : "Item #" + itemId;
        long pricePerItemRaw = currentQuantity > 0 ? offer.getSpent() / currentQuantity : offer.getPrice();
        backendClient.sendPortfolioState(slot, itemId, itemName, state.name(), currentQuantity, pricePerItemRaw);

        // Detect when an offer completes (bought or sold)
        if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
        {
            int deltaQuantity = currentQuantity - lastOfferQuantity[slot];
            if (deltaQuantity > 0)
            {
                boolean isBuy = (state == GrandExchangeOfferState.BOUGHT);
                long pricePerItem = currentQuantity > 0 ? offer.getSpent() / currentQuantity : 0;

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

                // Queue for crowdsourced contribution (batched POST every 15s)
                if (backendClient != null)
                {
                    backendClient.queueTrade(itemId, pricePerItem, deltaQuantity, isBuy);
                }

                log.info("GE {} detected: {}x {} @ {}gp (slot {})",
                    isBuy ? "buy" : "sell", deltaQuantity, itemName, pricePerItem, slot);

                // Record GE trade event in debug manager
                if (debugManager != null)
                {
                    debugManager.recordEvent(isBuy ? "GE_BUY" : "GE_SELL",
                        String.format("%dx @ %dgp (slot %d)", deltaQuantity, pricePerItem, slot),
                        itemName);
                }

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

        // Track slot activity time for slot timer display
        slotLastActive[slot] = Instant.now();
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

        // Handle crowdsourced data toggle
        if ("enableCrowdsourced".equals(event.getKey()) && backendClient != null)
        {
            if (config.enableCrowdsourced())
            {
                backendClient.start();
            }
            else
            {
                backendClient.stop();
            }
        }

        // Handle backend URL change
        if ("backendUrl".equals(event.getKey()) && backendClient != null)
        {
            backendClient.setBackendUrl(config.backendUrl());
        }

        // Handle market signal config changes
        if ("enableNeuralNet".equals(event.getKey()) && marketSignalClient != null)
        {
            marketSignalClient.setEnabled(config.enableNeuralNet());
            log.info("Market signal engine {}", config.enableNeuralNet() ? "enabled" : "disabled");
        }
        if ("neuralNetUrl".equals(event.getKey()) && marketSignalClient != null)
        {
            marketSignalClient.setSignalUrl(config.neuralNetUrl());
            log.info("Market signal URL updated → {}", config.neuralNetUrl());
        }

        // Handle profile API key change (login/logout)
        if ("profileApiKey".equals(event.getKey()) && profileClient != null)
        {
            String newKey = config.profileApiKey();
            if (newKey != null && !newKey.isEmpty())
            {
                profileClient.login(newKey);
            }
            else
            {
                profileClient.logout();
            }
            if (profilePanel != null)
            {
                profilePanel.refreshDashboard();
            }
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
        else if (config.dumpScanHotkey().matches(e))
        {
            // Ctrl+Shift+D: Immediately scan for dump opportunities
            executor.execute(this::runDumpScan);
            e.consume();
        }
        else if (e.isControlDown() && e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_U)
        {
            // Ctrl+Shift+U: Toggle debug overlay
            if (debugOverlay != null)
            {
                config.enableDebugOverlay();
                debugManager.info(getClass().getSimpleName(), "Debug overlay toggled");
            }
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
        else if (config.suggestionPreviewHotkey().matches(e))
        {
            // Ctrl+Shift+G: Preview top suggestion (copy price to clipboard + chat hint)
            executor.execute(this::previewSuggestion);
            e.consume();
        }
        else if (config.marginCheckHotkey().matches(e))
        {
            // Ctrl+Shift+M: Copy margin check price to clipboard
            executor.execute(this::runMarginCheck);
            e.consume();
        }
        else if (config.copyBuyPriceHotkey().matches(e))
        {
            copyPriceAssist(true);
            e.consume();
        }
        else if (config.copySellPriceHotkey().matches(e))
        {
            copyPriceAssist(false);
            e.consume();
        }
        else if (config.copySlotAssistHotkey().matches(e))
        {
            copySlotAssistBlock();
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        // Not used
    }

    // ==================== SUGGESTION PREVIEW ====================

    /**
     * Preview top flip suggestion — copies buy price to clipboard, pre-fills
     * GE price helper, and shows a chat hint with margin check prices.
     *
     * <p><b>INFORMATION ONLY</b> — the player must manually open the GE and place
     * the offer themselves. The price is only copied to clipboard + shown in chat.</p>
     */
    private void previewSuggestion()
    {
        try
        {
            List<FlipSuggestionEngine.FlipSuggestion> suggestions = suggestionEngine.generateSuggestions();
            if (suggestions.isEmpty())
            {
                log.debug("No flip suggestions available");
                return;
            }

            FlipSuggestionEngine.FlipSuggestion top = suggestions.get(0);

            // Use GePriceHelper to copy price and prepare auto-fill
            if (gePriceHelper != null)
            {
                gePriceHelper.suggestPrice(top.getItemId(), true);
            }
            else
            {
                // Fallback: copy buy price to clipboard
                String priceStr = String.valueOf(top.getBuyPrice());
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(priceStr);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }

            // Get margin check info
            long[] marginPrices = gePriceHelper != null ? gePriceHelper.getMarginCheckPrices(top.getItemId()) : null;
            String marginCheckInfo = "";
            if (marginPrices != null)
            {
                marginCheckInfo = String.format(" | MC: Buy@%s Sell@%s",
                    formatGpChat(marginPrices[0]), formatGpChat(marginPrices[1]));
            }

            // Calculate estimated GP/hr
            long estimatedGpPerHour = 0;
            if (top.getMargin() > 0 && top.getBuyLimit() > 0)
            {
                // Assume ~4hr limit cycle, factor in 1 buy + 1 sell per cycle
                estimatedGpPerHour = top.getProfitPerLimit() / 4;
            }

            // Send chat message with enhanced suggestion details
            String message = String.format(
                "<col=ff9800>Grand Flip Out:</col> Buy <col=00ff00>%s</col> @ <col=ffffff>%s gp</col> " +
                "(qty: %d, margin: %s gp, profit/limit: %s gp, ~%s gp/hr%s)",
                top.getItemName(),
                formatGpChat(top.getBuyPrice()),
                top.getBuyLimit(),
                formatGpChat(top.getMargin()),
                formatGpChat(top.getProfitPerLimit()),
                formatGpChat(estimatedGpPerHour),
                marginCheckInfo
            );

            clientThread.invokeLater(() -> {
                client.addChatMessage(
                    net.runelite.api.ChatMessageType.GAMEMESSAGE,
                    "",
                    message,
                    null
                );
            });

            log.debug("Suggestion previewed: Buy {}x {} @ {}gp (margin: {}gp, ~{}gp/hr)",
                top.getBuyLimit(), top.getItemName(), top.getBuyPrice(), top.getMargin(), estimatedGpPerHour);
        }
        catch (Exception e)
        {
            log.warn("Failed to preview suggestion: {}", e.getMessage());
        }
    }

    /**
     * Run a margin check: copies the instant-buy price to clipboard and shows
     * margin check instructions in chat.
     *
     * <p><b>INFORMATION ONLY</b> — Margin checking is a standard flipping technique
     * where you buy 1 item at a high price and sell 1 item at a low price to
     * discover the current spread. The player does all trades manually.</p>
     */
    private void runMarginCheck()
    {
        try
        {
            // Use the top suggestion's item for margin check if no active flip
            FlipSuggestionEngine.FlipSuggestion top = null;
            List<FlipSuggestionEngine.FlipSuggestion> suggestions = suggestionEngine.generateSuggestions();
            if (!suggestions.isEmpty())
            {
                top = suggestions.get(0);
            }

            if (top == null)
            {
                log.debug("No items available for margin check");
                return;
            }

            long[] prices = gePriceHelper != null ? gePriceHelper.getMarginCheckPrices(top.getItemId()) : null;
            if (prices == null)
            {
                return;
            }

            // Copy the instant-buy price to clipboard
            String priceStr = String.valueOf(prices[0]);
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(priceStr);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

            // Record the margin check
            if (marginCheckTracker != null)
            {
                marginCheckTracker.recordMarginCheck(top.getItemId(), top.getItemName(), prices[0], prices[1]);
            }

            String message = String.format(
                "<col=ff9800>Grand Flip Out:</col> <col=00ff00>%s</col> — " +
                "Buy 1 @ <col=ffffff>%s</col> (copied), then sell 1 @ <col=ffffff>%s</col> to check margin",
                top.getItemName(),
                formatGpChat(prices[0]),
                formatGpChat(prices[1])
            );

            clientThread.invokeLater(() -> {
                client.addChatMessage(
                    net.runelite.api.ChatMessageType.GAMEMESSAGE,
                    "",
                    message,
                    null
                );
            });
        }
        catch (Exception e)
        {
            log.warn("Margin check failed: {}", e.getMessage());
        }
    }

    private String formatGpChat(long amount)
    {
        if (Math.abs(amount) >= 1_000_000)
        {
            return String.format("%.1fm", amount / 1_000_000.0);
        }
        if (Math.abs(amount) >= 1_000)
        {
            return String.format("%.1fk", amount / 1_000.0);
        }
        return String.valueOf(amount);
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

            // Seed price history for top items from Wiki timeseries API
            // This bootstraps EMA/RSI/MACD/Bollinger analysis immediately
            log.info("Seeding price history from Wiki timeseries API...");
            priceService.getHistoryCollector().seedTopItems(50);

            panel.updateAll();

            long duration = System.currentTimeMillis() - startMs;
            log.info("Initial price data loaded: {} items, history seeded for top 50",
                priceService.getAggregatedPrices().size());

            if (debugManager != null)
            {
                debugManager.recordOperationTime("initialPriceLoad", duration);
                debugManager.info("PriceService",
                    String.format("Initial load: %d items in %dms",
                        priceService.getAggregatedPrices().size(), duration));
            }
        }
        catch (Exception e)
        {
            log.error("Failed to load initial price data", e);
            if (debugManager != null)
            {
                debugManager.error("PriceService", "Initial load failed: " + e.getMessage());
            }
        }
    }

    private void refreshPrices()
    {
        long refreshStart = System.currentTimeMillis();
        try
        {
            priceService.refreshAll();
            pollPredictionTelemetryIfDue();

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
                            log.debug("Dump detected: {} — Action: {}, Confidence: {}",
                                alert.getItemName(),
                                analysis.getRecommendedAction(),
                                analysis.getConfidence());

                            // Record dump event in debug manager
                            if (debugManager != null)
                            {
                                debugManager.recordEvent("DUMP_DETECTED",
                                    String.format("Action: %s, Confidence: %s",
                                        analysis.getRecommendedAction(), analysis.getConfidence()),
                                    alert.getItemName());
                            }
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
            if (debugPanel != null)
            {
                debugPanel.refresh();
            }

            // Record refresh cycle performance + memory snapshot
            if (debugManager != null)
            {
                long refreshDuration = System.currentTimeMillis() - refreshStart;
                debugManager.recordOperationTime("refreshPrices", refreshDuration);

                Runtime rt = Runtime.getRuntime();
                debugManager.recordMemorySnapshot(
                    rt.totalMemory() - rt.freeMemory(),
                    rt.maxMemory(),
                    priceService.getAggregatedPrices().size(),
                    priceService.getHistoryCollector() != null
                        ? priceService.getHistoryCollector().getTrackedItemCount() : 0
                );
            }

            log.debug("Price data refreshed");
        }
        catch (Exception e)
        {
            log.warn("Failed to refresh prices: {}", e.getMessage());
            if (debugManager != null)
            {
                debugManager.error("refreshPrices", "Failed: " + e.getMessage());
            }
        }
    }

    /**
     * Poll backend prediction model quality on a low cadence for debug visibility.
     * Runs on the plugin refresh executor thread (not game thread).
     */
    private void pollPredictionTelemetryIfDue()
    {
        if (debugManager == null || okHttpClient == null || gson == null || config == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPredictionStatsPollMs < 5 * 60_000L) // every 5 minutes
        {
            return;
        }
        lastPredictionStatsPollMs = now;

        String baseUrl = config.backendUrl();
        if (baseUrl == null || baseUrl.isEmpty())
        {
            return;
        }
        baseUrl = baseUrl.replace("/api/contribute", "");
        String url = baseUrl + "/api/predict/stats";

        Request request = new Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "GrandFlipOut/2.0.0 RuneLite")
            .build();

        long start = System.currentTimeMillis();
        try (Response response = okHttpClient.newCall(request).execute())
        {
            long duration = System.currentTimeMillis() - start;
            if (!response.isSuccessful() || response.body() == null)
            {
                debugManager.recordAPICall("backend/predict/stats", duration, false);
                return;
            }

            String body = response.body().string();
            var json = gson.fromJson(body, com.google.gson.JsonObject.class);
            double hitRate = 0.0;
            double mape = 0.0;
            long resolved = 0L;
            if (json != null && json.has("performance") && json.get("performance").isJsonObject())
            {
                var perf = json.getAsJsonObject("performance");
                if (perf.has("directionHitRate")) hitRate = perf.get("directionHitRate").getAsDouble();
                if (perf.has("mape")) mape = perf.get("mape").getAsDouble();
            }
            if (json != null && json.has("resolvedPredictions"))
            {
                resolved = json.get("resolvedPredictions").getAsLong();
            }

            debugManager.recordAPICall("backend/predict/stats", duration, true);
            debugManager.recordEvent(
                "PREDICTION_MODEL",
                String.format("Hit %.1f%% | MAPE %.2f%% | Resolved %d", hitRate, mape, resolved),
                null
            );
        }
        catch (IOException ex)
        {
            long duration = System.currentTimeMillis() - start;
            debugManager.recordAPICall("backend/predict/stats", duration, false);
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
                log.debug("Dump scan: No active dumps detected");
                return;
            }

            log.debug("Dump scan: {} dump(s) detected", dumpAlerts.size());

            for (var alert : dumpAlerts)
            {
                var knowledgeAlert = convertToDumpKnowledgeAlert(alert);
                var analysis = dumpKnowledgeEngine.analyzeDump(knowledgeAlert);
                log.debug("  {} — {} (confidence: {}, target buy: {}, recovery: {})",
                    alert.getItemName(),
                    analysis.getRecommendedAction(),
                    analysis.getConfidence(),
                    analysis.getTargetBuyPrice(),
                    analysis.getExpectedRecoveryPrice());

                // Log per-account GE limit availability (informational only)
                if (config.enableMultiAccount())
                {
                    var availability = multiAccountDashboard.getGeLimitAvailability(alert.getItemId());
                    log.debug("  Per-account GE limit availability for {}: {}", alert.getItemName(), availability);
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
                int buyLimit = agg.getBuyLimit();
                return new PriceContext(
                    agg.getItemName(),
                    agg.getBestLowPrice(),
                    agg.getBestHighPrice(),
                    buyLimit
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

    /**
     * Converts DumpDetector alert to DumpKnowledgeEngine format.
     * FIXES: averageVolume now set (was 0, broke isHighVolume);
     *        previousPrice derived from actual deviation (was hardcoded 5%).
     */
    private DumpKnowledgeEngine.PriceAlert convertToDumpKnowledgeAlert(DumpDetector.PriceAlert alert)
    {
        var agg = priceService.getPrice(alert.getItemId());
        long currentPrice = agg != null ? agg.getCurrentPrice() : 0;
        long hourlyVolume = agg != null ? agg.getTotalVolume1h() : 0;

        // Derive previous price from deviation: current = previous * (1 + dev/100)
        double deviation = alert.getLowDeviation();
        long previousPrice;
        if (Math.abs(deviation) > 0.01 && currentPrice > 0)
        {
            previousPrice = (long) (currentPrice / (1.0 + deviation / 100.0));
        }
        else
        {
            previousPrice = currentPrice;
        }

        return DumpKnowledgeEngine.PriceAlert.builder()
            .itemId(alert.getItemId())
            .itemName(alert.getItemName())
            .currentPrice(currentPrice)
            .previousPrice(previousPrice)
            .volume(hourlyVolume)
            .averageVolume(hourlyVolume) // FIX: was missing - isHighVolume() always returned true
            .buyPrice(agg != null ? agg.getBestLowPrice() : currentPrice)
            .sellPrice(agg != null ? agg.getBestHighPrice() : currentPrice)
            .priceMovement(deviation)
            .build();
    }
}