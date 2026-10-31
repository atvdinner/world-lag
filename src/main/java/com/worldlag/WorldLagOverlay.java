package com.worldlag;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * World Lag's box: the world and its input lag - how long before the next
 * tick a click has to go in to count (WorldLagStats.deadlineMs). Two lines
 * (kept small beside other info boxes), or one; the number's colour says
 * if it's good. Alt + drag moves it (any overlay's).
 */
class WorldLagOverlay extends OverlayPanel {
  private final WorldLagPlugin plugin;
  private final WorldLagConfig config;

  @Inject
  WorldLagOverlay(WorldLagPlugin plugin, WorldLagConfig config) {
    super(plugin);
    this.plugin = plugin;
    this.config = config;
    setPosition(OverlayPosition.TOP_LEFT);
  }

  /** The number's colour (gray: still measuring). */
  static Color shade(WorldLagConfig c, int lag) {
    return lag < 0 ? Color.GRAY : lag <= c.goodMs() ? c.goodColor() : lag <= c.badMs() ? c.okColor() : c.badColor();
  }

  @Override
  public Dimension render(Graphics2D g) {
    int lag = plugin.stats.deadlineMs();
    Font font = config.font().font();
    g.setFont(font);
    panelComponent.setBackgroundColor(config.backgroundColor());
    if (config.showWorld()) panelComponent.getChildren().add(TitleComponent.builder().text("World " + plugin.world()).color(config.textColor()).build());
    panelComponent.getChildren().add(LineComponent.builder()
        .left("Input lag").leftColor(config.textColor()).leftFont(font)
        .right(lag < 0 ? "measuring" : lag + " ms").rightColor(shade(config, lag)).rightFont(font).build());
    panelComponent.setPreferredSize(new Dimension(g.getFontMetrics(font).stringWidth("Input lag  measuring") + 12, 0));
    return super.render(g);
  }
}
