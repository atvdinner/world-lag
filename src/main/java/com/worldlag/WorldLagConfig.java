package com.worldlag;

import java.awt.Color;
import java.awt.Font;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.ComponentConstants;

/** World Lag's options - here (the wrench) and in its side panel
 *  (WorldLagPanel), the same settings. */
@ConfigGroup(WorldLagConfig.GROUP)
public interface WorldLagConfig extends Config {
  String GROUP = "worldlag";

  enum TextFont {
    REGULAR("Regular"),
    BOLD("Bold"),
    SMALL("Small");

    private final String name;
    TextFont(String name) { this.name = name; }
    Font font() { return this == BOLD ? FontManager.getRunescapeBoldFont() : this == SMALL ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont(); }
    @Override public String toString() { return name; }
  }

  @ConfigSection(name = "The box", description = "How the input lag box looks", position = 0)
  String box = "box";
  @ConfigSection(name = "Colours", description = "The box's colours", position = 10)
  String colours = "colours";
  @ConfigSection(name = "Measuring", description = "What counts as good or bad, and over how long", position = 20)
  String measuring = "measuring";

  @ConfigItem(keyName = "showWorld", name = "Show the world", description = "A \"World 404\" line over the input lag. Off: one line.", section = box, position = 1)
  default boolean showWorld() { return true; }

  @ConfigItem(keyName = "font", name = "Font", description = "The box's font", section = box, position = 2)
  default TextFont font() { return TextFont.REGULAR; }

  @ConfigItem(keyName = "goodColor", name = "Good", description = "The number's colour when the input lag is good", section = colours, position = 11)
  default Color goodColor() { return new Color(0x0dc10d); }

  @ConfigItem(keyName = "okColor", name = "So-so", description = "The number's colour between good and bad", section = colours, position = 12)
  default Color okColor() { return new Color(0xffff00); }

  @ConfigItem(keyName = "badColor", name = "Bad", description = "The number's colour when the input lag is bad", section = colours, position = 13)
  default Color badColor() { return new Color(0xff3030); }

  @ConfigItem(keyName = "textColor", name = "Text", description = "The world and \"Input lag\"", section = colours, position = 14)
  default Color textColor() { return Color.WHITE; }

  @Alpha
  @ConfigItem(keyName = "backgroundColor", name = "Background", description = "The box's background", section = colours, position = 15)
  default Color backgroundColor() { return ComponentConstants.STANDARD_BACKGROUND_COLOR; }

  @ConfigItem(keyName = "goodMs", name = "Good up to", description = "Input lag up to this is good. A normal world measured 125-185 ms.", section = measuring, position = 21)
  @Range(min = 50, max = 600)
  @Units(Units.MILLISECONDS)
  default int goodMs() { return 190; }

  @ConfigItem(keyName = "badMs", name = "Bad above", description = "Input lag above this is bad. World 404 measured 225-285 ms.", section = measuring, position = 22)
  @Range(min = 50, max = 600)
  @Units(Units.MILLISECONDS)
  default int badMs() { return 220; }

  @ConfigItem(keyName = "minutes", name = "Measure over", description = "The input lag of this many minutes of play. Shorter reacts faster, longer is steadier.", section = measuring, position = 23)
  @Range(min = 2, max = 30)
  @Units(Units.MINUTES)
  default int minutes() { return 5; }
}
