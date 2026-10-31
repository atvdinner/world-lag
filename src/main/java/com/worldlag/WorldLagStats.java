package com.worldlag;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * World Lag's numbers over the last few minutes (no RuneLite in here, so it
 * can be tested on its own):
 *  - the click deadline: how long before the next tick shows a click has to
 *    be made to make it. Each timed click is how far into its tick it was
 *    made (ms after that tick arrived) and how many ticks later its change
 *    came back (1 = the next). The deadline's point in the tick is where
 *    "made it" turns into "missed": the cut that the fewest clicks disagree
 *    with. Needs a few misses near it before it's trusted;
 *  - late clicks: clicks made well before the deadline (150+ ms) that still
 *    came back a tick or more late;
 *  - late ticks: ticks arriving 700+ ms after the one before - the ones
 *    during a frame stall of your own PC counted apart.
 * Measured on recorded raids: world 404 250 ms / 9.5% / 1.5%, worlds
 * 416 / 618 / 487 125-150 ms / ~2% / 0.3%.
 */
final class WorldLagStats {
  static final double TICK_MS = 600;
  static final double LATE_TICK_MS = 700;
  /** Clicks this far before the deadline count toward late clicks. */
  static final double CLEAR_MS = 150;
  static final int MIN_CLICKS = 30;
  static final int MIN_MISSES = 5;

  private static final class Click {
    final double at, phase;
    final int ticks;
    Click(double at, double phase, int ticks) { this.at = at; this.phase = phase; this.ticks = ticks; }
  }
  private static final class Gap {
    final double at, ms;
    final boolean pcStall;
    Gap(double at, double ms, boolean pcStall) { this.at = at; this.ms = ms; this.pcStall = pcStall; }
  }

  private final Deque<Click> clicks = new ArrayDeque<>();
  private final Deque<Gap> gaps = new ArrayDeque<>();

  void addClick(double at, double phaseMs, int ticks) { clicks.addLast(new Click(at, phaseMs, ticks)); }
  void addTickGap(double at, double gapMs, boolean pcStall) { gaps.addLast(new Gap(at, gapMs, pcStall)); }

  /** Forget what's older than the window. */
  void trim(double now, double windowMs) {
    while (!clicks.isEmpty() && clicks.peekFirst().at < now - windowMs) clicks.removeFirst();
    while (!gaps.isEmpty() && gaps.peekFirst().at < now - windowMs) gaps.removeFirst();
  }
  void clear() { clicks.clear(); gaps.clear(); }

  int clickCount() { return clicks.size(); }
  int tickCount() { return gaps.size(); }

  /** Where in the tick clicks stop making the next one (ms after it
   *  arrived), or -1: not enough yet. */
  double cutoffPhase() {
    if (clicks.size() < MIN_CLICKS) return -1;
    int best = Integer.MAX_VALUE, lo = 0, hi = 0;
    for (int t = 0; t <= 600; t += 10) {
      int err = 0;
      for (Click c : clicks) if ((c.phase < t) != (c.ticks == 1)) err++;
      if (err < best) { best = err; lo = t; hi = t; }
      else if (err == best) hi = t;
    }
    double cut = (lo + hi) / 2.0;
    // (Trusted only with misses near it: clicks made after it that missed.)
    int misses = 0;
    for (Click c : clicks) if (c.phase >= cut && c.phase < cut + 200 && c.ticks > 1) misses++;
    return misses >= MIN_MISSES ? cut : -1;
  }

  /** The click deadline, ms before the next tick shows; -1: not enough yet. */
  int deadlineMs() {
    double cut = cutoffPhase();
    return cut < 0 ? -1 : (int) Math.round(TICK_MS - cut);
  }

  /** Late clicks, %: made clear of the deadline, still a tick or more late
   *  (-1: none to judge by). */
  double lateClickPct() {
    double cut = cutoffPhase();
    double clear = cut < 0 ? 200 : cut - CLEAR_MS;
    int n = 0, late = 0;
    for (Click c : clicks) if (c.phase < clear) { n++; if (c.ticks > 1) late++; }
    return n < 10 ? -1 : 100.0 * late / n;
  }

  /** Late ticks, % of ticks: the world's (pc = false) or during your own
   *  PC's frame stalls (pc = true). */
  double lateTickPct(boolean pc) {
    if (gaps.isEmpty()) return 0;
    int late = 0;
    for (Gap g : gaps) if (g.ms >= LATE_TICK_MS && g.pcStall == pc) late++;
    return 100.0 * late / gaps.size();
  }
}
