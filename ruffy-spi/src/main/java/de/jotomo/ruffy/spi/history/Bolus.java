package de.jotomo.ruffy.spi.history;

public class Bolus extends HistoryRecord {
    public final double amount;

    public Bolus(long timestamp, double amount) {
        super(timestamp);
        this.amount = amount;
    }
}