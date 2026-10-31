package com.worldlag;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Cursor;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;

/**
 * World Lag's side panel: the input lag now and on the worlds you've been
 * on this session, what it is, and the box's options (the same settings as
 * the wrench's - either changes both).
 */
class WorldLagPanel extends PluginPanel {
  private static final String[] KEYS = {"showWorld", "font", "goodColor", "okColor", "badColor", "textColor", "backgroundColor", "goodMs", "badMs", "minutes"};

  private final WorldLagConfig config;
  private final ConfigManager configManager;
  private final ColorPickerManager pickers;

  private final JLabel world = new JLabel();
  private final JLabel lag = new JLabel();
  private final JLabel from = new JLabel();
  private final JPanel session = new Fit(new GridLayout(0, 2, 0, 2));
  private final JCheckBox showWorld = new JCheckBox("Show the world");
  private final JComboBox<WorldLagConfig.TextFont> font = new JComboBox<>(WorldLagConfig.TextFont.values());
  private final JSpinner goodMs = new JSpinner(new SpinnerNumberModel(190, 50, 600, 10));
  private final JSpinner badMs = new JSpinner(new SpinnerNumberModel(220, 50, 600, 10));
  private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(5, 2, 30, 1));
  private final JLabel[] colours = new JLabel[5];
  /** (Setting the controls from the config: not a change of the user's.) */
  private boolean filling;
  /** The numbers as of the last tick (copied on the client's thread). */
  private int nowWorld = -1, nowLag = -1, nowClicks;
  private Map<Integer, Integer> nowSeen = new LinkedHashMap<>();

  WorldLagPanel(WorldLagConfig config, ConfigManager configManager, ColorPickerManager pickers) {
    this.config = config;
    this.configManager = configManager;
    this.pickers = pickers;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // Now.
    JPanel now = card();
    world.setFont(FontManager.getRunescapeBoldFont());
    world.setForeground(ColorScheme.TEXT_COLOR);
    lag.setFont(FontManager.getRunescapeBoldFont().deriveFont(24f));
    from.setFont(FontManager.getRunescapeSmallFont());
    from.setForeground(Color.GRAY);
    for (JLabel l : new JLabel[] {world, lag, from}) { l.setAlignmentX(Component.CENTER_ALIGNMENT); now.add(l); }
    add(heading("Input lag now"));
    add(fit(now));
    add(Box.createVerticalStrut(8));
    add(heading("This session"));
    session.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    session.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    session.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(fit(session));

    add(Box.createVerticalStrut(8));
    add(heading("What is input lag?"));
    add(text("The game runs in ticks, one every 0.6 seconds. A click only counts on the next tick if it gets to the server in time - too late and it waits a whole extra tick."));
    add(text("Input lag is how long before the next tick you have to click for it to count: your ping, plus how long the world's server takes to take your click in. Everything you do waits on it - prayers, switches, eating, moving."));
    add(text("Normal worlds measure about 125-185 ms. A busy world (404, at its worst) measured 225-285 ms: about a sixth of every tick gone."));
    add(text("It's measured from the prayer, gear and food clicks you already make - which tick each one's change came back on. It needs about 30 of them (a few minutes of fighting) before it shows a number."));

    add(Box.createVerticalStrut(8));
    add(heading("The box"));
    JPanel opts = new Fit(new GridBagLayout());
    opts.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    opts.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    showWorld.setOpaque(false);
    showWorld.setForeground(ColorScheme.TEXT_COLOR);
    showWorld.addActionListener(e -> set("showWorld", showWorld.isSelected()));
    font.addActionListener(e -> set("font", font.getSelectedItem()));
    row(opts, null, showWorld);
    row(opts, "Font", font);
    font.setPreferredSize(new Dimension(92, 22));
    font.setMinimumSize(new Dimension(92, 22));
    String[] names = {"Good colour", "So-so colour", "Bad colour", "Text colour", "Background"};
    String[] keys = {"goodColor", "okColor", "badColor", "textColor", "backgroundColor"};
    for (int i = 0; i < 5; i++) {
      final String key = keys[i], name = names[i];
      final boolean alpha = key.equals("backgroundColor");
      // (A swatch: click it for RuneLite's colour picker.)
      JLabel b = new JLabel();
      b.setOpaque(true);
      b.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
      b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      b.setToolTipText("Click to change");
      b.addMouseListener(new MouseAdapter() {
        @Override public void mouseClicked(MouseEvent e) {
          RuneliteColorPicker p = pickers.create(WorldLagPanel.this, b.getBackground(), name + " colour", !alpha);
          p.setLocationRelativeTo(b);
          p.setOnColorChange(c -> set(key, c));
          p.setVisible(true);
        }
      });
      colours[i] = b;
      row(opts, name, b);
    }
    row(opts, "Good up to", goodMs);
    row(opts, "Bad above", badMs);
    row(opts, "Minutes", minutes);
    goodMs.setToolTipText("Input lag up to this many ms is good (green). Normal worlds measured 125-185.");
    badMs.setToolTipText("Input lag above this many ms is bad (red). World 404 measured 225-285.");
    minutes.setToolTipText("The input lag of the last this-many minutes of play.");
    goodMs.addChangeListener(e -> set("goodMs", goodMs.getValue()));
    badMs.addChangeListener(e -> set("badMs", badMs.getValue()));
    minutes.addChangeListener(e -> set("minutes", minutes.getValue()));
    add(fit(opts));
    JButton reset = new JButton("Reset to defaults");
    reset.setAlignmentX(Component.LEFT_ALIGNMENT);
    reset.addActionListener(e -> { for (String k : KEYS) configManager.unsetConfiguration(WorldLagConfig.GROUP, k); });
    add(Box.createVerticalStrut(4));
    add(reset);
    add(text("To move the box: hold Alt and drag it."));

    refreshOptions();
    refresh();
  }

  /** A row of the options: its name, then its control (62 px wide). */
  private int rows;
  private void row(JPanel p, String name, JComponent control) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridy = rows++;
    c.insets = new Insets(2, 0, 2, 0);
    c.fill = GridBagConstraints.HORIZONTAL;
    if (name == null) { c.gridx = 0; c.gridwidth = 2; c.weightx = 1; p.add(control, c); return; }
    JLabel l = new JLabel(name);
    l.setForeground(ColorScheme.TEXT_COLOR);
    c.gridx = 0; c.weightx = 1;
    p.add(l, c);
    c.gridx = 1; c.weightx = 0;
    control.setPreferredSize(new Dimension(62, 22));
    // (Too narrow for both: the name gives way, never the control.)
    control.setMinimumSize(new Dimension(62, 22));
    l.setMinimumSize(new Dimension(0, 22));
    p.add(control, c);
  }
  /** A box never stretched taller than it needs (BoxLayout would). */
  private static final class Fit extends JPanel {
    Fit(LayoutManager l) { super(l); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
  }
  private static JComponent fit(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    return c;
  }

  private static JLabel heading(String s) {
    JLabel l = new JLabel(s);
    l.setFont(FontManager.getRunescapeBoldFont());
    l.setForeground(ColorScheme.BRAND_ORANGE);
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    return l;
  }
  private static JLabel text(String s) {
    JLabel l = new JLabel("<html><body style='width: " + (PANEL_WIDTH - 62) + "px'>" + s + "</body></html>");
    l.setFont(FontManager.getRunescapeSmallFont());
    l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
    return l;
  }
  private static JPanel card() {
    JPanel p = new Fit(null);
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    p.setAlignmentX(Component.LEFT_ALIGNMENT);
    return p;
  }

  private void set(String key, Object value) {
    if (filling) return;
    configManager.setConfiguration(WorldLagConfig.GROUP, key, value);
  }

  /** The options as the config has them (after either place changed one). */
  void refreshOptions() {
    filling = true;
    try {
      showWorld.setSelected(config.showWorld());
      font.setSelectedItem(config.font());
      Supplier<?>[] c = {config::goodColor, config::okColor, config::badColor, config::textColor, config::backgroundColor};
      for (int i = 0; i < 5; i++) colours[i].setBackground((Color) c[i].get());
      goodMs.setValue(config.goodMs());
      badMs.setValue(config.badMs());
      minutes.setValue(config.minutes());
    } finally { filling = false; }
  }

  /** The numbers as of this tick (on Swing's thread). */
  void show(int world, int lag, int clicks, Map<Integer, Integer> seen) {
    nowWorld = world; nowLag = lag; nowClicks = clicks; nowSeen = seen;
    refresh();
  }
  void refresh() {
    int ms = nowLag;
    world.setText(nowWorld > 0 ? "World " + nowWorld : "Not logged in");
    lag.setText(ms < 0 ? "measuring" : ms + " ms");
    lag.setForeground(WorldLagOverlay.shade(config, ms));
    from.setText(nowClicks + " clicks timed, last " + config.minutes() + " min");
    session.removeAll();
    for (Map.Entry<Integer, Integer> e : nowSeen.entrySet()) {
      JLabel w = new JLabel("World " + e.getKey());
      w.setForeground(ColorScheme.TEXT_COLOR);
      JLabel v = new JLabel(e.getValue() + " ms", SwingConstants.RIGHT);
      v.setForeground(WorldLagOverlay.shade(config, e.getValue()));
      session.add(w); session.add(v);
    }
    if (nowSeen.isEmpty()) { JLabel none = new JLabel("No worlds measured yet"); none.setForeground(Color.GRAY); session.add(none); }
    session.revalidate();
    session.repaint();
  }
}
