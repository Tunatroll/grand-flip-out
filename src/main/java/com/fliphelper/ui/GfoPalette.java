package com.fliphelper.ui;

import java.awt.Color;

/**
 * Grand Flip Out brand palette — the ONE color SSOT for every plugin panel.
 *
 * Mirrors the site's token SSOT (website/css/gfo.css): GFO is dark + PASTEL —
 * lilac accent, seafoam-mint profit, pastel-rose loss, orchid dump, cool
 * near-black panels. The previous per-panel constants were the GRANARY
 * wheat-gold theme (a different project's identity — owner 2026-07-10: "this
 * isnt granary this is grand flip out") duplicated in three files; kanban #104
 * is the site-side twin of this retirement.
 *
 * The RuneLite sidebar's native DARK_GRAY base stays as the outermost
 * background (the plugin lives inside the client); these tokens carry the
 * BRAND: accents, cards, semantics, borders, text.
 */
public final class GfoPalette
{
    private GfoPalette() {}

    /** --gfo-accent: primary action / brand (lilac). */
    public static final Color ACCENT = new Color(0xA7, 0x8B, 0xFA);
    /** --gfo-accent-100: soft tint for text-on-dark accents. */
    public static final Color ACCENT_SOFT = new Color(0xC9, 0xB8, 0xFF);
    /** --gfo-accent-2: secondary accent / data highlight (light blue). */
    public static final Color ACCENT_2 = new Color(0x7D, 0xD3, 0xFC);

    /** --gfo-panel: base panel (cool near-black). */
    public static final Color PANEL = new Color(0x14, 0x13, 0x20);
    /** --gfo-panel-raised: raised panel / card. */
    public static final Color CARD = new Color(0x1C, 0x1A, 0x2B);
    /** --gfo-panel-elevated: elevated / selected / hover. */
    public static final Color ELEVATED = new Color(0x28, 0x25, 0x3C);

    /** --gfo-up: profit / positive (seafoam mint). */
    public static final Color UP = new Color(0x5E, 0xEA, 0xD4);
    /** --gfo-down: loss / negative (pastel rose). */
    public static final Color DOWN = new Color(0xFB, 0x7D, 0xA3);
    /** --gfo-dump: dump / special event (soft pastel orchid). */
    public static final Color DUMP = new Color(0xE0, 0xAA, 0xF5);

    /** --gfo-border: default hairline on dark. */
    public static final Color BORDER = new Color(0x2C, 0x29, 0x42);
    /** --gfo-text-muted: secondary text / labels (lavender-gray). */
    public static final Color TEXT_MUTED = new Color(0x9D, 0x98, 0xBC);
    /** --gfo-text: primary text (soft white). */
    public static final Color TEXT = new Color(0xEC, 0xEA, 0xF6);
    /** --gfo-text-dim: tertiary / disabled / axis labels. */
    public static final Color TEXT_DIM = new Color(0x8B, 0x86, 0xA8);
}
