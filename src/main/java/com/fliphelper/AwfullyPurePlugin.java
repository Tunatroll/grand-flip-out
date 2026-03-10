package com.fliphelper;

import com.fliphelper.api.ApHttpBatcher;
import com.fliphelper.api.BackendClient;
import com.fliphelper.api.PeerNetwork;
import com.fliphelper.api.ProfileClient;
import com.fliphelper.api.PriceService;
import com.fliphelper.ui.ProfilePanel;
import com.fliphelper.ui.DebugPanel;
import com.fliphelper.ui.GpDropOverlay;
import com.fliphelper.ui.SlotsPanel;
import com.fliphelper.ui.StatsPanel;
import com.fliphelper.debug.DebugManager;
import com.fliphelper.debug.DebugOverlay;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.FlipState;
import com.fliphelper.model.TradeRecord;
import com.fliphelper.tracker.*;
import com.fliphelper.ui.AwfullyPurePanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.RuneLite.RUNELITE_DIR;


@Slf4j
@PluginDescriptor(
    name = "Grand Flip Out",
    description = "Comprehensive GE flipping assistant with multi-source pricing, flip tracking, profit analysis, and smart suggestions",
    tags = {"grand exchange", "flipping", "merching", "prices", "profit", "ge", "trading"}
)
public class AwfullyPurePlugin extends Plugin implements KeyListener
{
    private static final File DATA_DIR = new File(RUNELITE_DIR, "awfully-pure");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private AwfullyPureConfig config;

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
    private PeerNetwork peerNetwork;
    private ProfileClient profileClient;
    private BackendClient backendClient;
    private ApHttpBatcher httpBatcher;
    private AccountDataManager accountDataManager;
    private GEOfferHelper geOfferHelper;
    private DebugManager debugManager;
    private DebugPanel debugPanel;
    private SlotsPanel slotsPanel;
    private StatsPanel statsPanel;
    private AwfullyPurePanel panel;
    private ProfilePanel profilePanel;
    private AwfullyPureOverlay overlay;
    private DebugOverlay debugOverlay;
    private GpDropOverlay gpDropOverlay;
    private NavigationButton navButton;
    private ScheduledExecutorService executor;
    private int currentAccountIndex = 0;

    // Track GE offer states to detect completions
    private final int[] lastOfferQuantity = new int[8];
    private final GrandExchangeOfferState[] lastOfferState = new GrandExchangeOfferState[8];

    @Provides
    AwfullyPureConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AwfullyPureConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        log.info("Awfully Pure starting up");

        DATA_DIR.mkdirs();

        // Initialize debug manager with local file logging
        debugManager = new DebugManager();
        debugManager.setDataDir(DATA_DIR);
        debugManager.info(getClass().getSimpleName(), "Plugin startup initiated");

        // Initialize core services
        priceService = new PriceService(okHttpClient, config, gson);
        priceService.setDebugManager(debugManager);

        // Initialize per-account data manager (auto-detects RSN on login)
        accountDataManager = new AccountDataManager(DATA_DIR, gson);

        flipTracker = new FlipTracker(config, DATA_DIR, gson);
        flipTracker.setDebugManager(debugManager);
        // Wire profile flip logging — when a buy→sell pair completes, log it to the user's profile
        // Routes through ProfileClient → PeerNetwork for P2P failover
        flipTracker.setFlipCompleteListener(flip -> {
            // Track lifetime profit per account
            if (accountDataManager != null)
            {
                accountDataManager.addCompletedFlipProfit(flip.getProfit());
            }

            // Log to profile server for cross-device sync
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

        // Initialize GE offer helper (price-set hotkeys + slot timers)
        geOfferHelper = new GEOfferHelper(client, clientThread, config, priceService, accountDataManager);

        // Initialize SmartAdvisor — the unified intelligence brain
        smartAdvisor = new SmartAdvisor(
            priceService, marketIntelligence, botEconomyTracker,
            jagexTradeIndex, dumpDetector, dumpKnowledgeEngine,
            dataQualityAnalyzer, riskManager, investmentHorizonAnalyzer,
            priceService.getHistoryCollector()
        );

        // Create UI
        panel = new AwfullyPurePanel(config, priceService, flipTracker, suggestionEngine, smartAdvisor);

        // Create Slots Panel (GE slot timers, account switcher) — the first tab users see
        slotsPanel = new SlotsPanel(config, client, priceService, flipTracker, accountDataManager, geOfferHelper);
        panel.insertTab("Slots", slotsPanel, 0);
        panel.setSelectedTabIndex(0); // Default to Slots tab

        // Create Stats Panel (per-item trading statistics — avg buy/sell, volume, profit)
        statsPanel = new StatsPanel(flipTracker, accountDataManager, priceService);
        panel.insertTab("Stats", statsPanel, 2); // After Slots and Smart

        // ProfilePanel added after initialization below (was null ref bug)
        overlay = new AwfullyPureOverlay(client, config, priceService, flipTracker);
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

        // P2P Network (fundamental to Awfully Pure architecture)
        peerNetwork = new PeerNetwork(okHttpClient, gson);
        if (config.enableP2P())
        {
            // Parse additional peers from config (comma-separated)
            List<String> extraPeers = Arrays.stream(config.additionalPeers().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

            // Filter to HTTPS only for security
            List<String> validPeers = new ArrayList<>();
            for (String peer : extraPeers)
            {
                if (peer.toLowerCase().startsWith("https://"))
                {
                    validPeers.add(peer);
                }
                else
                {
                    log.warn("Rejected non-HTTPS peer: {}", peer);
                }
            }

            // Always add the configured backend URL as a seed peer
            String backendBase = config.backendUrl().replace("/api/contribute", "");
            if (backendBase.toLowerCase().startsWith("https://"))
            {
                validPeers.add(backendBase);
            }
            else
            {
                log.warn("Rejected non-HTTPS backend peer: {}", backendBase);
            }

            peerNetwork.start(validPeers);
            log.info("P2P network started with {} valid HTTPS seed(s)", validPeers.size());
        }

        // Profile & Account Client (tier gating, account management)
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

        // Profile Panel (account UI tab)
        profilePanel = new ProfilePanel(profileClient, peerNetwork);
        panel.addTab("Profile", profilePanel);  // Now safe — profilePanel is initialized

        // Debug Panel (live stats + debug report)
        debugPanel = new DebugPanel(debugManager);
        panel.addTab("Debug", debugPanel);

        // Crowdsourced BackendClient (batched trade contributions)
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

        // HTTP Batcher (batched JTI/ZScore events → backend)
        httpBatcher = new ApHttpBatcher(okHttpClient, gson);
        httpBatcher.setBackendBaseUrl(config.backendUrl());
        httpBatcher.start();

        // Start background price refresh
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.execute(this::initialPriceLoad);
        executor.scheduleAtFixedRate(
            this::refreshPrices,
            config.priceRefreshInterval(),
            config.priceRefreshInterval(),
            TimeUnit.SECONDS
        );

        log.info("Awfully Pure started successfully");
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Awfully Pure shutting down");

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

        if (httpBatcher != null)
        {
            httpBatcher.stop();
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

        // Save per-account data on shutdown
        if (accountDataManager != null)
        {
            accountDataManager.saveAll();
        }

        // Stop slots panel timer
        if (slotsPanel != null)
        {
            slotsPanel.stopTimer();
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
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            // Auto-detect RSN on login
            clientThread.invokeLater(() -> {
                Player localPlayer = client.getLocalPlayer();
                if (localPlayer != null && localPlayer.getName() != null)
                {
                    String rsn = localPlayer.getName();
                    accountDataManager.onAccountLogin(rsn);
                    log.info("Account detected: {}", rsn);
                    if (slotsPanel != null) slotsPanel.refreshAccountList();
                    panel.updateAll();
                }
            });
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            // Save and clear active account on logout
            accountDataManager.onAccountLogout();
        }
    }

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

        // Update slot timer (GEOfferHelper tracks time since last activity)
        if (geOfferHelper != null)
        {
            geOfferHelper.onOfferChanged(slot, itemId);
        }

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
                if (statsPanel != null) statsPanel.refresh();

                // Record to per-account storage (auto-persists to <rsn>.json)
                if (accountDataManager != null)
                {
                    accountDataManager.recordTrade(trade);
                }

                // Auto-detect margin checks: 1-qty trades are margin checks
                // Buy-1 at high price = instant-buy check (tells you the sell price)
                // Sell-1 at low price = instant-sell check (tells you the buy price)
                if (deltaQuantity == 1 && accountDataManager != null)
                {
                    if (isBuy)
                    {
                        // 1-qty buy = finding the instant-buy price (= what sellers offer)
                        // This becomes the sell check price for margin calculation
                        AccountDataManager.MarginCheckResult existing =
                            accountDataManager.getLastMarginCheck(itemId);
                        if (existing != null && !existing.isFresh())
                        {
                            // Start fresh margin check
                            accountDataManager.recordMarginCheck(itemId, 0, pricePerItem);
                        }
                        else if (existing == null)
                        {
                            accountDataManager.recordMarginCheck(itemId, 0, pricePerItem);
                        }
                        else
                        {
                            // Update the sell-check side
                            accountDataManager.recordMarginCheck(itemId,
                                existing.getBuyCheckPrice(), pricePerItem);
                        }
                        log.info("Margin check (buy-1): {} @ {} gp", itemName, pricePerItem);
                    }
                    else
                    {
                        // 1-qty sell = finding the instant-sell price (= what buyers offer)
                        // This becomes the buy check price for margin calculation
                        AccountDataManager.MarginCheckResult existing =
                            accountDataManager.getLastMarginCheck(itemId);
                        if (existing != null)
                        {
                            accountDataManager.recordMarginCheck(itemId,
                                pricePerItem, existing.getSellCheckPrice());
                        }
                        else
                        {
                            accountDataManager.recordMarginCheck(itemId, pricePerItem, 0);
                        }
                        log.info("Margin check (sell-1): {} @ {} gp", itemName, pricePerItem);
                    }
                }

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
                        // GE tax: 2% of sell price per item, capped at 5M per item (not per transaction)
                        long taxPerItem = Math.min((long)(pricePerItem * 0.02), 5_000_000L);
                        long totalTax = taxPerItem * deltaQuantity;
                        long profit = sellTotal - buyTotal - totalTax;
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
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"awfullypure".equals(event.getGroup()))
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
        else if (config.setBuyPriceHotkey().matches(e))
        {
            // "E" key (default): Set buy price in GE from margin check / API data
            if (geOfferHelper != null && geOfferHelper.isGEOpen() && geOfferHelper.isChatboxInputActive())
            {
                geOfferHelper.setBuyPrice();
                e.consume();
            }
        }
        else if (config.setSellPriceHotkey().matches(e))
        {
            // "R" key (default): Set sell price in GE from margin check / API data
            if (geOfferHelper != null && geOfferHelper.isGEOpen() && geOfferHelper.isChatboxInputActive())
            {
                geOfferHelper.setSellPrice();
                e.consume();
            }
        }
        else if (config.setMaxQuantityHotkey().matches(e))
        {
            // "Q" key (default): Set quantity to GE buy limit
            if (geOfferHelper != null && geOfferHelper.isGEOpen() && geOfferHelper.isChatboxInputActive())
            {
                geOfferHelper.setMaxQuantity();
                e.consume();
            }
        }
        else if (config.addFavoriteHotkey().matches(e))
        {
            // Ctrl+F: Toggle favorite for currently viewed item
            if (accountDataManager != null && geOfferHelper != null)
            {
                // Toggle favorite via account data manager
                log.info("Toggle favorite triggered");
            }
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        // Not used
    }

    // ==================== SUGGESTION PREVIEW ====================

    
    private void previewSuggestion()
    {
        try
        {
            List<FlipSuggestionEngine.FlipSuggestion> suggestions = suggestionEngine.generateSuggestions();
            if (suggestions.isEmpty())
            {
                log.info("No flip suggestions available");
                return;
            }

            FlipSuggestionEngine.FlipSuggestion top = suggestions.get(0);

            // Copy buy price to clipboard for easy paste into GE
            String priceStr = String.valueOf(top.getBuyPrice());
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(priceStr);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

            // Send chat message with suggestion details
            String message = String.format(
                "<col=ff9800>AP:</col> Buy <col=00ff00>%s</col> @ <col=ffffff>%s gp</col> (qty: %d, margin: %s gp, profit/limit: %s gp)",
                top.getItemName(),
                formatGpChat(top.getBuyPrice()),
                top.getBuyLimit(),
                formatGpChat(top.getMargin()),
                formatGpChat(top.getProfitPerLimit())
            );

            clientThread.invokeLater(() -> {
                client.addChatMessage(
                    net.runelite.api.ChatMessageType.GAMEMESSAGE,
                    "",
                    message,
                    null
                );
            });

            log.info("Suggestion previewed: Buy {}x {} @ {}gp (margin: {}gp)",
                top.getBuyLimit(), top.getItemName(), top.getBuyPrice(), top.getMargin());
        }
        catch (Exception e)
        {
            log.warn("Failed to preview suggestion: {}", e.getMessage());
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
