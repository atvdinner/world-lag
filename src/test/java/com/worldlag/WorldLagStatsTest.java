package com.worldlag;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import org.junit.Test;

/** WorldLagStats against a world like 404 as recorded: clicks made in the
 *  first 350 ms of a tick make the next one (a 250 ms deadline), 9% of
 *  those still a tick late; 1.5% of ticks late. */
public class WorldLagStatsTest {
  @Test
  public void world404() {
    Random r = new Random(1);
    WorldLagStats s = new WorldLagStats();
    for (int i = 0; i < 400; i++) {
      double phase = r.nextDouble() * 600;
      int ticks = phase < 350 ? (r.nextDouble() < 0.09 ? 2 : 1) : 2;
      s.addClick(i * 600, phase, ticks);
    }
    for (int i = 0; i < 1000; i++) s.addTickGap(i * 600, i % 200 < 3 ? 720 : 600, false);
    assertEquals(250, s.deadlineMs(), 20);
    assertEquals(9, s.lateClickPct(), 3);
    assertEquals(1.5, s.lateTickPct(false), 0.01);
    assertEquals(0, s.lateTickPct(true), 0.01);
  }

  @Test
  public void notEnoughYet() {
    WorldLagStats s = new WorldLagStats();
    for (int i = 0; i < 20; i++) s.addClick(i, 100, 1);
    assertEquals(-1, s.deadlineMs());
    // (Plenty of clicks, but none late in the tick: no misses to place it.)
    for (int i = 0; i < 40; i++) s.addClick(i, 50 + i, 1);
    assertEquals(-1, s.deadlineMs());
  }

  @Test
  public void forgetsOldClicks() {
    WorldLagStats s = new WorldLagStats();
    for (int i = 0; i < 50; i++) s.addClick(i * 1000, 100, 1);
    s.trim(60_000, 30_000);
    assertEquals(20, s.clickCount());
  }
}
