package com.loghog;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public final class LatencyStatistics {

    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

    private LatencyStatistics() {
    }

    public static LatencySummary computeScalar(int[] values) {
        if (values.length == 0) {
            return new LatencySummary(0.0, 0.0, 0);
        }

        long sum = 0L;
        long sumSquares = 0L;
        for (int value : values) {
            long asLong = value;
            sum += asLong;
            sumSquares += asLong * asLong;
        }

        double count = values.length;
        double mean = sum / count;
        double variance = Math.max(0.0, (sumSquares / count) - (mean * mean));
        return new LatencySummary(mean, Math.sqrt(variance), values.length);
    }

    public static LatencySummary computeVector(int[] values) {
        if (values.length == 0) {
            return new LatencySummary(0.0, 0.0, 0);
        }

        long[] longs = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            longs[i] = values[i];
        }

        long sum = 0L;
        long sumSquares = 0L;
        int upperBound = SPECIES.loopBound(longs.length);
        int i = 0;
        for (; i < upperBound; i += SPECIES.length()) {
            LongVector vector = LongVector.fromArray(SPECIES, longs, i);
            sum += vector.reduceLanes(VectorOperators.ADD);
            sumSquares += vector.mul(vector).reduceLanes(VectorOperators.ADD);
        }

        for (; i < longs.length; i++) {
            long value = longs[i];
            sum += value;
            sumSquares += value * value;
        }

        double count = values.length;
        double mean = sum / count;
        double variance = Math.max(0.0, (sumSquares / count) - (mean * mean));
        return new LatencySummary(mean, Math.sqrt(variance), values.length);
    }

    public static BenchmarkResult benchmark(int[] values) {
        int iterations = values.length == 0 ? 1 : 3;

        long scalarStart = System.nanoTime();
        LatencySummary scalarSummary = null;
        for (int i = 0; i < iterations; i++) {
            scalarSummary = computeScalar(values);
        }
        long scalarEnd = System.nanoTime();

        long vectorStart = System.nanoTime();
        LatencySummary vectorSummary = null;
        for (int i = 0; i < iterations; i++) {
            vectorSummary = computeVector(values);
        }
        long vectorEnd = System.nanoTime();

        if (scalarSummary != null && vectorSummary != null
                && Double.isNaN(scalarSummary.mean() + vectorSummary.mean())) {
            throw new IllegalStateException("Unexpected benchmark state");
        }

        return new BenchmarkResult(
                (scalarEnd - scalarStart) / 1_000_000.0,
                (vectorEnd - vectorStart) / 1_000_000.0);
    }
}
