package com.sulthan.cardash;

/**
 * Decides when a driving warning should go off: engine too hot, battery low, battery not charging,
 * or faster than the chosen speed. Pure logic (no Android), fed about once a second.
 *
 * Each warning needs its condition to hold for a few seconds before it fires, fires once, and only
 * re-arms after the reading has clearly recovered, so a value hovering at the limit does not chime
 * over and over.
 */
final class AlertRules {
    enum Kind { COOLANT, LOW_BATTERY, NOT_CHARGING, SPEED }

    interface Sink {
        /** A warning starts. [value] is the reading that set it off (°C, volts or km/h). */
        void raise(Kind kind, double value);
        /** The reading recovered; a banner still on screen for it can go. */
        void clear(Kind kind);
    }

    /** The user's limits. NaN switches that warning off. */
    static final class Limits {
        double coolantC = Double.NaN;
        /** Battery warnings on or off (the thresholds below are fixed). */
        boolean battery;
        double speedKmh = Double.NaN;
    }

    static final double LOW_BATTERY_V = 11.8;
    static final double NOT_CHARGING_V = 12.8;
    static final double ENGINE_RUNNING_RPM = 500;

    private static final long COOLANT_HOLD_MS = 5_000;
    private static final long LOW_BATTERY_HOLD_MS = 15_000;
    private static final long NOT_CHARGING_HOLD_MS = 30_000;
    private static final long SPEED_HOLD_MS = 3_000;

    private final Rule[] rules = new Rule[Kind.values().length];

    AlertRules() {
        for (Kind k : Kind.values()) rules[k.ordinal()] = new Rule();
    }

    void update(long nowMs, double speedKmh, double coolantC, double volts, double rpm, Limits lim, Sink sink) {
        boolean running = !Double.isNaN(rpm) && rpm >= ENGINE_RUNNING_RPM;
        boolean stopped = Double.isNaN(rpm) || rpm < ENGINE_RUNNING_RPM;

        step(Kind.COOLANT, nowMs, coolantC, sink, COOLANT_HOLD_MS,
                !Double.isNaN(lim.coolantC) && coolantC >= lim.coolantC,
                Double.isNaN(lim.coolantC) || coolantC < lim.coolantC - 3);

        step(Kind.LOW_BATTERY, nowMs, volts, sink, LOW_BATTERY_HOLD_MS,
                lim.battery && stopped && volts < LOW_BATTERY_V,
                !lim.battery || volts >= LOW_BATTERY_V + 0.3);

        step(Kind.NOT_CHARGING, nowMs, volts, sink, NOT_CHARGING_HOLD_MS,
                lim.battery && running && volts < NOT_CHARGING_V,
                !lim.battery || volts >= NOT_CHARGING_V + 0.2 || !running);

        step(Kind.SPEED, nowMs, speedKmh, sink, SPEED_HOLD_MS,
                !Double.isNaN(lim.speedKmh) && speedKmh >= lim.speedKmh,
                Double.isNaN(lim.speedKmh) || speedKmh < lim.speedKmh - 3);
    }

    /**
     * [bad] is true while the warning condition holds (always false for a missing reading, since
     * NaN comparisons are false). [ok] is true once the reading has recovered past the margin.
     */
    private void step(Kind k, long now, double value, Sink sink, long holdMs, boolean bad, boolean ok) {
        Rule r = rules[k.ordinal()];
        if (Double.isNaN(value)) {
            // No reading (adapter dropped, GPS lost): forget the timer but stay fired, so a
            // reconnect at the same bad value does not chime a second time.
            r.since = -1;
            return;
        }
        if (r.fired) {
            if (ok) {
                r.fired = false;
                r.since = -1;
                sink.clear(k);
            }
            return;
        }
        if (!bad) {
            r.since = -1;
            return;
        }
        if (r.since < 0) r.since = now;
        if (now - r.since >= holdMs) {
            r.fired = true;
            sink.raise(k, value);
        }
    }

    boolean isFired(Kind k) { return rules[k.ordinal()].fired; }

    private static final class Rule {
        long since = -1;
        boolean fired;
    }
}
