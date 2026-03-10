package com.fliphelper.ui;

import com.fliphelper.AwfullyPureConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Floating GP drop overlay — shows animated profit text rising and fading
 * when a profitable flip completes. Pure visual celebration, no game interaction.
 */
@Slf4j
public class GpDropOverlay extends Overlay
{
    private static final int DROP_DURATION_MS = 2500;
    private static final int DROP_RISE_PIXELS = 80;
    private static final int MAX_CONCURRENT_DROPS = 4; // Cap to prevent UI clutter
    private static final Font GP_FONT = new Font("RuneScape Bold", Font.BOLD, 24);
    private static final Font GP_FONT_FALLBACK = new Font("Arial", Font.BOLD, 22);

    private final Client client;
    private final AwfullyPureConfig config;
    private final CopyOnWriteArrayList<GpDrop> activeDrops = new CopyOnWriteArrayList<>();

    @Inject
    public GpDropOverlay(Client client, AwfullyPureConfig config)
    {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGHEST);
    }

    /**
     * Trigger a GP drop animation.
     * @param profit The profit amount to display
     * @param itemName The item name for context
     */
    public void triggerDrop(long profit, String itemName)
    {
        if (profit <= 0)
        {
            return;
        }

        // Cap concurrent drops to prevent UI clutter
        if (activeDrops.size() >= MAX_CONCURRENT_DROPS)
        {
            return;
        }

        // Stagger multiple drops slightly
        int offsetX = activeDrops.size() * 30;
        activeDrops.add(new GpDrop(profit, itemName, System.currentTimeMillis(), offsetX));
        log.debug("GP drop triggered: {} from {}", formatGp(profit), itemName);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (activeDrops.isEmpty())
        {
            return null;
        }

        long now = System.currentTimeMillis();

        // Remove expired drops
        activeDrops.removeIf(drop -> (now - drop.startTime) > DROP_DURATION_MS);

        if (activeDrops.isEmpty())
        {
            return null;
        }

        // Render each active drop
        // Use client canvas dimensions for proper positioning
        int canvasWidth = client.getCanvasWidth();
        int canvasHeight = client.getCanvasHeight();
        int centerX = canvasWidth / 2;
        int baseY = canvasHeight / 4; // Upper quarter of screen

        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (GpDrop drop : activeDrops)
        {
            float progress = (float)(now - drop.startTime) / DROP_DURATION_MS;
            float alpha = 1.0f - (progress * progress); // Quadratic fade
            int yOffset = (int)(progress * DROP_RISE_PIXELS);

            if (alpha <= 0)
            {
                continue;
            }

            // Scale text size based on profit magnitude
            float scale = 1.0f;
            if (drop.profit >= 10_000_000) scale = 1.4f;
            else if (drop.profit >= 1_000_000) scale = 1.2f;
            else if (drop.profit >= 100_000) scale = 1.1f;

            Font font = GP_FONT_FALLBACK.deriveFont(GP_FONT_FALLBACK.getSize2D() * scale);
            g2.setFont(font);

            String text = "+" + formatGp(drop.profit) + " gp";
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int x = centerX - textWidth / 2 + drop.offsetX;
            int y = baseY - yOffset;

            // Draw outline (black shadow)
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.8f));
            g2.setColor(Color.BLACK);
            g2.drawString(text, x + 2, y + 2);
            g2.drawString(text, x - 1, y - 1);

            // Draw main text with gold/green color
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            Color gpColor;
            if (drop.profit >= 1_000_000)
            {
                gpColor = new Color(0, 255, 128); // Bright green for big profits
            }
            else
            {
                gpColor = new Color(255, 215, 0); // Gold for smaller profits
            }
            g2.setColor(gpColor);
            g2.drawString(text, x, y);

            // Draw item name below (smaller)
            if (drop.itemName != null && !drop.itemName.isEmpty())
            {
                Font smallFont = font.deriveFont(font.getSize2D() * 0.6f);
                g2.setFont(smallFont);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.7f));
                g2.setColor(Color.WHITE);
                FontMetrics sfm = g2.getFontMetrics();
                int nameWidth = sfm.stringWidth(drop.itemName);
                g2.drawString(drop.itemName, centerX - nameWidth / 2 + drop.offsetX, y + 20);
            }
        }

        g2.dispose();
        return null;
    }

    private String formatGp(long amount)
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

    private static class GpDrop
    {
        final long profit;
        final String itemName;
        final long startTime;
        final int offsetX;

        GpDrop(long profit, String itemName, long startTime, int offsetX)
        {
            this.profit = profit;
            this.itemName = itemName;
            this.startTime = startTime;
            this.offsetX = offsetX;
        }
    }
}
