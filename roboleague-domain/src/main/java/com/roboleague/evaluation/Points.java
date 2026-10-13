package com.roboleague.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of points of a score: an item's subtotal, a section, an attempt's total or a team's total in a challenge.
 * It is a decimal with {@value #SCALE} decimals (hundredths of a point), so adding the same amounts in any order gives
 * the same total and two equal totals tie. What does not fit in hundredths, such as a product of a rate and a time,
 * is rounded once with {@link RoundingMode#HALF_EVEN}, which does not bias a sum of many rounded items upwards.
 * Measurements and factors (times, precision, weights, per-second rates) are not points.
 */
public final class Points implements Comparable<Points> {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final Points ZERO = new Points(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Points(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Points of(BigDecimal amount) {
        return new Points(Objects.requireNonNull(amount, "amount cannot be null"));
    }

    public static Points of(long amount) {
        return new Points(BigDecimal.valueOf(amount));
    }

    /**
     * The points written by a double, read by its shortest decimal representation: {@code of(0.1)} is 0.10.
     */
    public static Points of(double amount) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("points must be a finite number: " + amount);
        }
        return new Points(BigDecimal.valueOf(amount));
    }

    public static Points of(String amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        try {
            return new Points(new BigDecimal(amount.strip()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("points must be a decimal number: '" + amount + "'", e);
        }
    }

    /**
     * Whether a decimal is already in hundredths, so turning it into points does not round it.
     */
    public static boolean fitsExactly(BigDecimal amount) {
        return amount.stripTrailingZeros().scale() <= SCALE;
    }

    public Points plus(Points other) {
        return new Points(amount.add(other.amount));
    }

    public Points minus(Points other) {
        return new Points(amount.subtract(other.amount));
    }

    public Points negate() {
        return new Points(amount.negate());
    }

    public Points abs() {
        return new Points(amount.abs());
    }

    /**
     * These points scaled by a factor (a precision, a weight, a count), rounded once to hundredths.
     */
    public Points times(BigDecimal factor) {
        return new Points(amount.multiply(Objects.requireNonNull(factor, "factor cannot be null")));
    }

    public Points times(long factor) {
        return new Points(amount.multiply(BigDecimal.valueOf(factor)));
    }

    public Points max(Points other) {
        return compareTo(other) >= 0 ? this : other;
    }

    public Points min(Points other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Points other) {
        return compareTo(other) > 0;
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    /**
     * For a boundary that only takes doubles, such as a JSON number; never to add or compare points.
     */
    public double doubleValue() {
        return amount.doubleValue();
    }

    @Override
    public int compareTo(Points other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Points points && amount.equals(points.amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
