package com.sulthan.cardash;

/**
 * Drops GPS speed readings that jump more than a car can accelerate or brake. Indoors and under
 * poor reception the GPS sometimes reports a single burst of 50-70 km/h while the tablet sits
 * still, which was being recorded as a short drive. A reading is accepted only when it is close to
 * the reading just before it (both readings within a few seconds of each other), so a steady speed
 * is accepted from its second reading on and a lone spike never is.
 */
final class GpsSpeedFilter {
    /** About 0.4 g: more than ordinary driving produces, less than a GPS glitch. */
    static final double MAX_CHANGE_KMH_PER_SEC = 15.0;
    /** Readings further apart than this are not compared (the first after a gap is held back). */
    static final double MAX_GAP_SEC = 3.0;

    private double prevKmh = Double.NaN;
    private long prevMs;

    /** Returns [kmh] when it is plausible, else NaN. [nowMs] is a monotonic clock. */
    double accept(double kmh, long nowMs) {
        double prev = prevKmh;
        double dt = (nowMs - prevMs) / 1000.0;
        prevKmh = kmh;
        prevMs = nowMs;
        if (Double.isNaN(kmh) || Double.isNaN(prev) || dt <= 0 || dt > MAX_GAP_SEC) return Double.NaN;
        // Allow at least one second's worth of change, as fixes can arrive less than a second apart.
        return Math.abs(kmh - prev) <= MAX_CHANGE_KMH_PER_SEC * Math.max(dt, 1.0) ? kmh : Double.NaN;
    }

    void reset() {
        prevKmh = Double.NaN;
    }
}
