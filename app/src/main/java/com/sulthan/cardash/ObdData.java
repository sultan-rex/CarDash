package com.sulthan.cardash;

/** One snapshot of engine readings. Values are NaN until the car reports them. */
public final class ObdData {
    public double speedKmh = Double.NaN;
    public double rpm = Double.NaN;
    public double coolantC = Double.NaN;
    public double throttlePct = Double.NaN;
    public double loadPct = Double.NaN;
    public double intakeC = Double.NaN;
    public double voltage = Double.NaN;

    public ObdData copy() {
        ObdData d = new ObdData();
        d.speedKmh = speedKmh;
        d.rpm = rpm;
        d.coolantC = coolantC;
        d.throttlePct = throttlePct;
        d.loadPct = loadPct;
        d.intakeC = intakeC;
        d.voltage = voltage;
        return d;
    }
}
