package com.fliphelper.ui;

import net.runelite.client.ui.ColorScheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class GuidePanel extends JPanel {

    public GuidePanel() {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setEditable(false);
        textPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        textPane.setOpaque(false);
        textPane.setText("<html><body style='color: #b2b2b2; font-family: Tahoma, sans-serif; font-size: 13px; margin: 0; padding: 0;'>"
            + "<h2 style='color: #E0A82E; font-size: 15px; margin-bottom: 5px; margin-top: 0;'>Welcome to Grand Flip Out</h2>"
            + "Use this plugin alongside the <b>Intelligence Dashboard</b> on the website for maximum profit.<br><br>"
            
            + "<h3 style='color: white; margin-bottom: 2px;'>⚡ Smart Hotkeys</h3>"
            + "<ul style='margin-top: 2px; padding-left: 15px; margin-bottom: 8px;'>"
            + "<li><b>Ctrl+Shift+L:</b> Quick item lookup. Pops open the search and prefills the item under your cursor.</li>"
            + "<li><b>Ctrl+Shift+O:</b> Toggle in-game overlay.</li>"
            + "<li><b>Ctrl+Shift+M:</b> Copy margins of active item.</li>"
            + "</ul>"
            
            + "<h3 style='color: white; margin-bottom: 2px;'>🖱️ Mouse & Auto-Fill (Copilot Mode)</h3>"
            + "The Advisor tab provides a <b>Fill offer</b> button. Just open the Grand Exchange interface, and click the button to instantly prefill your offer price and quantity. (You always have to press Confirm yourself).<br><br>"
            + "You can also use the <b>Price-Fill Hotkey (Ctrl+Shift+P)</b> to push the suggested price into the GE box.<br><br>"
            
            + "<b>Note:</b> You can customize all hotkeys and toggle Auto-Fill in the RuneLite Plugin Settings (click the gear icon).<br><br>"
            + "<hr style='border: 1px solid #222;'>"
            + "<center>grandflipout.com</center>"
            + "</body></html>");

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(scrollPane, BorderLayout.CENTER);
    }
}
