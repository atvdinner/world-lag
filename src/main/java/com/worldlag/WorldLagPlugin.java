package com.worldlag;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * World Lag: why a world feels bad. Only shows you numbers - it never types,
 * sends or changes anything.
 *
 * It times the clicks you already make whose result the server sends back:
 * a prayer (Activate / Deactivate: varp 83), gear (Wear / Wield / Equip: the
 * equipment, container 94) and food or a potion (Eat / Drink: the
 * inventory, 93). For each, how far into its tick you clicked and which
 * tick's update brought the change. The server's update brings its changes
 * first and then the tick (GameTick), so a change marks its click and the
 * next GameTick is the tick it came in. WorldLagStats has the sums; the box
 * (WorldLagOverlay) shows one number, the input lag.
 */
@PluginDescriptor(
    name = "World Lag",
    description = "Your input lag on this world: how long before the next tick a click has to go in to count",
    tags = {"lag", "ping", "tick", "world", "delay"})
public class WorldLagPlugin extends Plugin {
  private static final int PRAYER = 0, WORN = 1, INV = 2;
  private static final int PRAYER_VARP = 83, INVENTORY = 93, EQUIPMENT = 94;
  /** A click whose change hasn't come back after this many ticks is dropped
   *  (it did nothing: a full inventory, an eat delay). */
  private static final int GIVE_UP = 4;
  private static final Pattern PRAYERS = Pattern.compile(
      "Quick-prayers|Protect|Piety|Rigour|Augury|Eagle|Mystic|Smite|Redemption|Retribution|Chivalry|Preserve|Rapid|Steel|Ultimate|Incredible|Hawk|Thick|Burst|Superhuman|Clarity|Sharp|Improved|Deadeye|Vigour");

  @Inject private Client client;
  @Inject private WorldLagConfig config;
  @Inject private ConfigManager configManager;
  @Inject private OverlayManager overlayManager;
  @Inject private WorldLagOverlay overlay;
  @Inject private ClientToolbar clientToolbar;
  @Inject private ColorPickerManager colorPickerManager;

  final WorldLagStats stats = new WorldLagStats();
  /** Each world's input lag this session (its latest). */
  final Map<Integer, Integer> seen = new LinkedHashMap<>();
  private WorldLagPanel panel;
  private NavigationButton button;

  private static final class Pending {
    final int kind, tick;
    final double phase;
    final String target;
    final boolean timed;
    boolean back;
    Pending(int kind, int tick, double phase, String target, boolean timed) {
      this.kind = kind; this.tick = tick; this.phase = phase; this.target = target; this.timed = timed;
    }
  }
  private final List<Pending> pending = new ArrayList<>();
  /** GameTicks seen, and when the last came (ms, -1: none since a load). */
  private int ticks;
  private double lastTickAt = -1;
  /** The longest gap between client frames since the last tick (a stall of
   *  your PC's, not the world's). */
  private double lastFrameAt = -1, worstFrameGap;
  private int world = -1;

  @Provides
  WorldLagConfig provideConfig(ConfigManager m) {
    return m.getConfig(WorldLagConfig.class);
  }

  @Override
  protected void startUp() {
    overlayManager.add(overlay);
    panel = new WorldLagPanel(config, configManager, colorPickerManager);
    button = NavigationButton.builder().tooltip("World Lag").icon(icon()).priority(10).panel(panel).build();
    clientToolbar.addNavigation(button);
  }

  /** The side bar's icon: a stopwatch. */
  private static BufferedImage icon() {
    BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = im.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(new Color(0xdc8a00));
    g.setStroke(new BasicStroke(1.6f));
    g.drawOval(2, 3, 12, 12);
    g.fillRect(6, 0, 4, 2);
    g.drawLine(8, 9, 8, 5);
    g.drawLine(8, 9, 11, 9);
    g.dispose();
    return im;
  }

  @Override
  protected void shutDown() {
    overlayManager.remove(overlay);
    clientToolbar.removeNavigation(button);
    reset();
    seen.clear();
  }

  private static double now() { return System.nanoTime() / 1e6; }

  private void reset() {
    stats.clear();
    pending.clear();
    lastTickAt = -1;
  }

  int world() { return world; }

  // --- The clicks, the changes they make, the ticks. ---
  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked e) {
    String op = e.getMenuOption();
    int kind;
    if ("Activate".equals(op) || "Deactivate".equals(op)) {
      if (!PRAYERS.matcher(Text.removeTags(e.getMenuTarget())).find()) return;
      kind = PRAYER;
    }
    else if ("Wear".equals(op) || "Wield".equals(op) || "Equip".equals(op)) kind = WORN;
    else if ("Eat".equals(op) || "Drink".equals(op)) kind = INV;
    else return;
    if (lastTickAt < 0) return;
    String target = Text.removeTags(e.getMenuTarget());
    // Timed: only the first of its kind in a tick (it decides the tick the
    // change comes in); a prayer turned on and off in one tick (a flick)
    // changes nothing, so neither is timed.
    boolean timed = true;
    for (Pending p : pending) {
      if (p.kind != kind || p.tick != ticks) continue;
      timed = false;
      if (kind == PRAYER && p.target.equals(target)) { pending.remove(p); break; }
    }
    pending.add(new Pending(kind, ticks, now() - lastTickAt, target, timed));
  }

  private void changed(int kind) {
    for (Pending p : pending) if (p.kind == kind) p.back = true;
  }
  @Subscribe
  public void onVarbitChanged(VarbitChanged e) {
    if (e.getVarpId() == PRAYER_VARP) changed(PRAYER);
  }
  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged e) {
    if (e.getContainerId() == EQUIPMENT) changed(WORN);
    else if (e.getContainerId() == INVENTORY) changed(INV);
  }

  @Subscribe
  public void onClientTick(ClientTick e) {
    double t = now();
    if (lastFrameAt > 0) worstFrameGap = Math.max(worstFrameGap, t - lastFrameAt);
    lastFrameAt = t;
  }

  @Subscribe
  public void onGameTick(GameTick e) {
    double t = now();
    if (client.getWorld() != world) { reset(); world = client.getWorld(); }
    if (lastTickAt > 0 && t - lastTickAt < 5000) stats.addTickGap(t, t - lastTickAt, worstFrameGap > 100);
    lastTickAt = t;
    worstFrameGap = 0;
    ticks++;
    for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
      Pending p = it.next();
      int n = ticks - p.tick;
      if (p.back) { if (p.timed) stats.addClick(t, p.phase, n); it.remove(); }
      else if (n > GIVE_UP) it.remove();
    }
    stats.trim(t, config.minutes() * 60_000.0);
    int lag = stats.deadlineMs();
    if (lag >= 0) { seen.remove(world); seen.put(world, lag); }
    if (panel != null) {
      final int w = world, n = stats.clickCount();
      final Map<Integer, Integer> copy = new LinkedHashMap<>(seen);
      SwingUtilities.invokeLater(() -> panel.show(w, lag, n, copy));
    }
  }

  @Subscribe
  public void onConfigChanged(ConfigChanged e) {
    if (WorldLagConfig.GROUP.equals(e.getGroup()) && panel != null) SwingUtilities.invokeLater(() -> { panel.refreshOptions(); panel.refresh(); });
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged e) {
    // (A load or a hop: the next tick's gap isn't the world's.)
    if (e.getGameState() != GameState.LOGGED_IN) { lastTickAt = -1; pending.clear(); }
  }
}
