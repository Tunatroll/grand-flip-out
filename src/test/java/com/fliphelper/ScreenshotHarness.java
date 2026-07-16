/*
 * Screenshot harness — renders the Grand Flip Out plugin panel offscreen to a PNG
 * for visual review, WITHOUT launching the full RuneLite client (mirror of the
 * desktop app's tools/screenshot.py and website/tools/screenshot.py).
 *
 * The plugin's UI is a Swing PluginPanel. We mock the injected deps (the config
 * interface is all `default` methods → a reflection Proxy returns the real
 * defaults), construct the real services + panel, and paint it to a BufferedImage
 * via printAll. Swing needs a graphics env, so run under xvfb-run.
 *
 * Run:  xvfb-run -a ./gradlew screenshot   (writes build/screenshots/panel.png)
 */
package com.fliphelper;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.ItemMapping;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;
import com.fliphelper.model.PriceSource;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.ui.GrandFlipOutPanel;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public final class ScreenshotHarness
{
    public static void main(String[] args) throws Exception
    {
        File out = new File(args.length > 0 ? args[0] : "build/screenshots/panel.png");
        out.getParentFile().mkdirs();

        // RuneLite runs the panel under a DARK LookAndFeel; this harness otherwise
        // defaults to Metal (light), which paints background-less components light and
        // makes the render lie. Force RuneLite-dark component/tabbed-pane defaults.
        java.awt.Color darkBg = net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR;
        for (String key : new String[]{
            "Panel.background", "Viewport.background", "ScrollPane.background",
            "TextField.background", "List.background", "ComboBox.background",
            "TabbedPane.background", "Label.background", "TextArea.background",
            "TabbedPane.contentAreaColor", "TabbedPane.light", "TabbedPane.highlight",
            "TabbedPane.shadow", "TabbedPane.darkShadow", "TabbedPane.focus",
            "TabbedPane.selected", "TabbedPane.selectHighlight",
            "TabbedPane.tabAreaBackground", "TabbedPane.unselectedBackground",
            "control", "controlHighlight", "controlLtHighlight",
            "controlShadow", "controlDkShadow"})
        {
            javax.swing.UIManager.put(key, darkBg);
        }

        // Config: methods are `default` → invoke them (real defaults). Non-default
        // methods (e.g. inherited Object/Config) get type-appropriate fallbacks.
        GrandFlipOutConfig config = (GrandFlipOutConfig) Proxy.newProxyInstance(
            GrandFlipOutConfig.class.getClassLoader(),
            new Class<?>[]{GrandFlipOutConfig.class},
            (proxy, method, mArgs) -> {
                if (method.isDefault())
                {
                    // Java 11-compatible default-method invocation from a Proxy.
                    return java.lang.invoke.MethodHandles
                        .privateLookupIn(GrandFlipOutConfig.class, java.lang.invoke.MethodHandles.lookup())
                        .unreflectSpecial(method, GrandFlipOutConfig.class)
                        .bindTo(proxy)
                        .invokeWithArguments(mArgs == null ? new Object[0] : mArgs);
                }
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) return false;
                if (rt == int.class) return 0;
                if (rt == long.class) return 0L;
                if (rt == double.class) return 0.0;
                if (rt == String.class) return "";
                if ("toString".equals(method.getName())) return "MockConfig";
                if ("hashCode".equals(method.getName())) return 0;
                if ("equals".equals(method.getName())) return proxy == (mArgs != null ? mArgs[0] : null);
                return null;
            });

        Gson gson = new Gson();
        OkHttpClient http = new OkHttpClient();
        File dataDir = Files.createTempDirectory("gfo-shot").toFile();

        // Opt-in (-Psample): inject sample F2P items so the Prices tab renders real cards
        // for visual review. Default render stays the clean empty panel.
        final boolean sample = Boolean.getBoolean("gfo.sample");
        final boolean advisor = Boolean.getBoolean("gfo.advisor"); // render the Advisor tab standalone
        final boolean basket = Boolean.getBoolean("gfo.basket");   // render the Advisor 8-slot basket
        final boolean firstRun = Boolean.getBoolean("gfo.firstrun");   // Advisor first-run teaser (public flips + enable)
        final boolean firstRun0 = Boolean.getBoolean("gfo.firstrun0"); // first-run STATIC pitch (network switch off)
        PriceService priceService = new PriceService(http, config, gson);
        if (sample)
        {
            injectSamplePrices(priceService);
        }
        FlipTracker flipTracker = new FlipTracker(config, priceService, dataDir, gson, Runnable::run); // direct executor — synchronous is fine offscreen

        final JComponent[] panelRef = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            JComponent target;
            if (advisor || basket || firstRun || firstRun0)
            {
                com.fliphelper.ui.AdvisorPanel ap = new com.fliphelper.ui.AdvisorPanel(
                    new com.fliphelper.ui.AdvisorPanel.Listener()
                    {
                        public void onSkip(int id) { }
                        public void onBlock(int id) { }
                        public void onPauseToggled(boolean p) { }
                        public void onFillOffer(int id, long price, int quantity) { }
                    });
                if (firstRun0)
                {
                    // Fresh install: master network switch off — static pitch, zero fetches.
                    ap.showFirstRun(null, false, () -> { });
                }
                else if (firstRun)
                {
                    ap.showFirstRun(sampleFirstRunFlips(), true, () -> { });
                }
                else if (basket)
                {
                    ap.showBasket(sampleBasket());
                    ap.showDumpFeed(sampleDumpFeed());
                }
                else
                {
                    ap.showSuggestion(sampleSuggestion());
                    ap.showDumpFeed(sampleDumpFeed());
                }
                target = ap;
            }
            else
            {
                GrandFlipOutPanel panel = new GrandFlipOutPanel(config, priceService, flipTracker);
                if (sample)
                {
                    panel.onEntitlementChanged();          // populate the gated Prices browse list
                }
                target = panel;
            }
            int w = Integer.getInteger("gfo.width", 240);  // RuneLite sidebar width (override for inspection)
            Dimension pref = target.getPreferredSize();
            // Width diagnostic: PluginPanel.getPreferredSize() hard-caps panels at 242px
            // (bytecode-verified 2026-07-16 investigating "client stretches without consent"
            // — the plugin CANNOT widen the sidebar; PanelWidthTest pins the contract).
            System.out.println("panel preferredSize=" + (pref != null ? pref.width + "x" + pref.height : "null")
                + (pref != null && pref.width > 242 ? "  ⚠ exceeds the 242px PluginPanel cap — should be impossible, investigate" : ""));
            int h = Math.max(700, pref != null ? pref.height : 700);
            target.setSize(w, h);
            target.doLayout();
            layoutRecursively(target);
            panelRef[0] = target;
        });

        JComponent panel = panelRef[0];
        int w = panel.getWidth(), h = panel.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        SwingUtilities.invokeAndWait(() -> panel.printAll(g));
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("saved " + out.getAbsolutePath() + " (" + w + "x" + h + ")");

        // Chunk B visual pass: one PNG per tab (walk to the JTabbedPane, select, paint).
        javax.swing.JTabbedPane tabs = findTabbedPane(panel);
        if (tabs != null)
        {
            for (int i = 0; i < tabs.getTabCount(); i++)
            {
                final int idx = i;
                SwingUtilities.invokeAndWait(() -> {
                    tabs.setSelectedIndex(idx);
                    panel.doLayout();
                    layoutRecursively(panel);
                });
                BufferedImage ti = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D tg = ti.createGraphics();
                SwingUtilities.invokeAndWait(() -> panel.printAll(tg));
                tg.dispose();
                String name = tabs.getTitleAt(idx).toLowerCase().replaceAll("[^a-z0-9]", "-");
                File tabOut = new File(out.getParentFile(), "tab-" + idx + "-" + name + ".png");
                ImageIO.write(ti, "png", tabOut);
                System.out.println("saved " + tabOut.getAbsolutePath());
            }

            // Chunk C: the Flips tab's HISTORY substate — the surface the tab loop never
            // reached, where the six-button header overlapped in the live build. Flip the
            // card toggle exactly like a user would and paint the result.
            for (int i = 0; i < tabs.getTabCount(); i++)
            {
                if (!"Flips".equals(tabs.getTitleAt(i)))
                {
                    continue;
                }
                final int idx = i;
                SwingUtilities.invokeAndWait(() -> {
                    tabs.setSelectedIndex(idx);
                    javax.swing.AbstractButton historyToggle = findButton(tabs.getComponentAt(idx), "History");
                    if (historyToggle != null)
                    {
                        historyToggle.doClick();
                    }
                    panel.doLayout();
                    layoutRecursively(panel);
                });
                BufferedImage hi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D hg = hi.createGraphics();
                SwingUtilities.invokeAndWait(() -> panel.printAll(hg));
                hg.dispose();
                File histOut = new File(out.getParentFile(), "tab-" + idx + "-flips-history.png");
                ImageIO.write(hi, "png", histOut);
                System.out.println("saved " + histOut.getAbsolutePath());
            }
        }
        System.exit(0);
    }

    /** Depth-first search for a button by its exact label (the card-pair toggles). */
    private static javax.swing.AbstractButton findButton(java.awt.Component root, String text)
    {
        if (root instanceof javax.swing.AbstractButton
            && text.equals(((javax.swing.AbstractButton) root).getText()))
        {
            return (javax.swing.AbstractButton) root;
        }
        if (root instanceof java.awt.Container)
        {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents())
            {
                javax.swing.AbstractButton hit = findButton(child, text);
                if (hit != null)
                {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * Inject a few F2P sample items (varied freshness) straight into PriceService's
     * private price map via reflection, so the offscreen render exercises real price
     * cards — including the data-freshness badge and confidence tooltip — without a
     * network fetch. Test-only; keeps production code free of screenshot hooks.
     */
    private static void injectSamplePrices(PriceService priceService) throws Exception
    {
        long now = System.currentTimeMillis() / 1000L;
        Map<Integer, PriceAggregate> prices = new HashMap<>();
        prices.put(1163, sample(1163, "Rune full helm", 21000, 20500, 1500, now - 30));      // fresh
        prices.put(1127, sample(1127, "Rune platebody", 38200, 37500, 900, now - 1500));      // ~25m
        prices.put(1079, sample(1079, "Rune platelegs", 38000, 37300, 300, now - 9000));      // ~2.5h stale

        Field f = PriceService.class.getDeclaredField("aggregatedPrices");
        f.setAccessible(true);
        f.set(priceService, prices);

        // isReady() also requires mappingsLoaded; flip it so the Prices tab renders.
        Field mapped = PriceService.class.getDeclaredField("mappingsLoaded");
        mapped.setAccessible(true);
        mapped.setBoolean(priceService, true);
    }

    // Suggestion grew 4 enrichment fields (marginPer, geLimit, profitPerLimit,
    // volume) after this harness was written, and its lombok all-args constructor
    // is package-private — build samples via the builder instead.
    private static com.fliphelper.model.Suggestion sampleSuggestion(
        String action, int itemId, String itemName, long price, int quantity,
        long expectedProfit, double confidence, java.util.List<String> reasons)
    {
        return com.fliphelper.model.Suggestion.builder()
            .action(action).itemId(itemId).itemName(itemName).price(price)
            .quantity(quantity).expectedProfit(expectedProfit).confidence(confidence)
            .reasons(reasons).targetSlot(-1)
            .build();
    }

    private static com.fliphelper.model.Suggestion sampleSuggestion()
    {
        return sampleSuggestion(
            "BUY", 1163, "Rune full helm", 20500, 70, 5600, 0.78,
            java.util.Arrays.asList("Margin clears the 2% tax", "Strong 1h volume", "Fresh price data"));
    }

    private static java.util.List<com.fliphelper.model.Suggestion> sampleBasket()
    {
        java.util.List<String> r = java.util.Arrays.asList("Margin clears the 2% tax", "Strong 1h volume");
        return java.util.Arrays.asList(
            sampleSuggestion("BUY", 1163, "Rune full helm", 20500, 24, 14400, 0.78, r),
            sampleSuggestion("BUY", 1127, "Rune platebody", 37500, 13, 9100, 0.71, r),
            sampleSuggestion("BUY", 1079, "Rune platelegs", 37300, 13, 7800, 0.66, r),
            sampleSuggestion("BUY", 1333, "Rune scimitar", 15200, 32, 6400, 0.74, r));
    }

    private static java.util.List<com.fliphelper.model.Suggestion> sampleFirstRunFlips()
    {
        // Mirrors the /api/plugin/suggestions parse in IntelligenceClient#fetchPublicTopFlips:
        // reasons[0] = the served "Buy at X -> Sell at Y" display line, marginPer = net/ea.
        return java.util.Arrays.asList(
            firstRunFlip(21948, "Dragonstone dragon bolts (e)", 2392, "Buy at 2,392 -> Sell at 2,564", 121),
            firstRunFlip(25578, "Soaked page", 3026, "Buy at 3,026 -> Sell at 3,206", 116),
            firstRunFlip(2577, "Ranger boots", 31409340, "Buy at 31,409,340 -> Sell at 33,200,000", 1126660),
            firstRunFlip(560, "Death rune", 205, "Buy at 205 -> Sell at 218", 9),
            firstRunFlip(32892, "Cupronickel bar", 2974, "Buy at 2,974 -> Sell at 3,325", 283));
    }

    private static com.fliphelper.model.Suggestion firstRunFlip(
        int itemId, String name, long buyPrice, String actionLine, long netPer)
    {
        return com.fliphelper.model.Suggestion.builder()
            .action("BUY").itemId(itemId).itemName(name).price(buyPrice).quantity(0)
            .expectedProfit(0).confidence(50)
            .reasons(java.util.Collections.singletonList(actionLine))
            .targetSlot(-1).marginPer(netPer)
            .build();
    }

    private static java.util.List<com.fliphelper.model.DumpFeedEntry> sampleDumpFeed()
    {
        return java.util.Arrays.asList(
            new com.fliphelper.model.DumpFeedEntry(1163, "Rune full helm", false, 20500, 22400L, 1450L, 0.84, -9.1, "likely"),
            new com.fliphelper.model.DumpFeedEntry(1127, "Rune platebody", false, 37500, 39900L, 1600L, 0.71, -6.4, "watch"),
            new com.fliphelper.model.DumpFeedEntry(1079, "Rune platelegs", false, 37300, null, null, null, -4.8, "watch"));
    }

    private static PriceAggregate sample(int id, String name, long high, long low, long vol1h, long tradeTime)
    {
        ItemMapping m = new ItemMapping();
        m.setId(id);
        m.setName(name);
        m.setMembers(false); // F2P so it shows for an anonymous (non-entitled) render
        m.setLimit(70);
        m.setHighalch((int) (low * 0.6));

        PriceData pd = PriceData.builder()
            .itemId(id).itemName(name)
            .highPrice(high).lowPrice(low)
            .highTime(tradeTime).lowTime(tradeTime - 15)
            .highVolume1h(vol1h).lowVolume1h(vol1h)
            .source(PriceSource.WIKI)
            .build();

        Map<PriceSource, PriceData> sources = new HashMap<>();
        sources.put(PriceSource.WIKI, pd);
        return PriceAggregate.builder().itemId(id).itemName(name).sourceData(sources).mapping(m).build();
    }

    /** Force layout of the whole tree so children have non-zero bounds before paint. */
    private static javax.swing.JTabbedPane findTabbedPane(java.awt.Container c)
    {
        for (java.awt.Component child : c.getComponents())
        {
            if (child instanceof javax.swing.JTabbedPane) return (javax.swing.JTabbedPane) child;
            if (child instanceof java.awt.Container)
            {
                javax.swing.JTabbedPane found = findTabbedPane((java.awt.Container) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void layoutRecursively(java.awt.Component c)
    {
        c.setSize(c.getPreferredSize().width > 0 ? c.getWidth() : 240,
                  c.getPreferredSize().height > 0 ? c.getPreferredSize().height : c.getHeight());
        if (c instanceof java.awt.Container)
        {
            ((java.awt.Container) c).doLayout();
            for (java.awt.Component child : ((java.awt.Container) c).getComponents())
            {
                layoutRecursively(child);
            }
        }
        if (c instanceof JComponent)
        {
            ((JComponent) c).validate();
        }
    }
}
