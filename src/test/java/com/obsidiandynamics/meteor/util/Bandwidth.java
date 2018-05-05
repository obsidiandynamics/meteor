package com.obsidiandynamics.meteor.util;

/**
 *  Helper for expressing bandwidth figures.
 */
public final class Bandwidth {
  public enum Unit {
    TBPS(1_000_000_000_000L, "Tbit/s"),
    GBPS(1_000_000_000, "Gbit/s"),
    MBPS(1_000_000, "Mbit/s"),
    KBPS(1_000, "kbit/s"),
    BPS(1, "bit/s");
    
    private final long bits;
    private final String symbol;
    
    private Unit(long bits, String symbol) { this.bits = bits; this.symbol = symbol; }

    public long getBits() {
      return bits;
    }

    public String getSymbol() {
      return symbol;
    }
  }
  
  /**
   *  A tuple comprising the rate and the unit of measurement.
   */
  public static final class RateAndUnit {
    private double rate;
    private Unit unit;
    
    public RateAndUnit(double rate, Unit unit) {
      this.rate = rate;
      this.unit = unit;
    }

    public double getRate() {
      return rate;
    }

    public void setRate(double rate) {
      this.rate = rate;
    }

    public Unit getUnit() {
      return unit;
    }

    public void setUnit(Unit unit) {
      this.unit = unit;
    }

    @Override
    public String toString() {
      return String.format("%,.1f %s", rate, unit.symbol);
    }
  }
  
  private Bandwidth() {}
  
  public static RateAndUnit translate(long bps) {
    final Unit unit;
    if (bps > Unit.TBPS.bits) {
      unit = Unit.TBPS;
    } else if (bps > Unit.GBPS.bits) {
      unit = Unit.GBPS;
    } else if (bps > Unit.MBPS.bits) {
      unit = Unit.MBPS;
    } else if (bps > Unit.KBPS.bits) {
      unit = Unit.KBPS;
    } else {
      unit = Unit.BPS;
    }
    return new RateAndUnit((double) bps / unit.bits, unit);
  }
}