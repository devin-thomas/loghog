package com.loghog;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AnalysisResult {

    private final IntArrayBuilder checkoutLatencies = new IntArrayBuilder();
    private final Set<String> errorIps = new LinkedHashSet<>();
    private long errorCount;
    private LatencySummary scalarSummary = new LatencySummary(0.0, 0.0, 0);
    private LatencySummary vectorSummary = new LatencySummary(0.0, 0.0, 0);
    private double scalarBenchmarkMillis;
    private double vectorBenchmarkMillis;

    public void merge(BatchMetrics batchMetrics) {
        errorCount += batchMetrics.errorCount();
        errorIps.addAll(batchMetrics.errorIps());
        checkoutLatencies.addAll(batchMetrics.checkoutLatencies());
    }

    public void finishStatistics() {
        int[] values = checkoutLatencies.toArray();
        scalarSummary = LatencyStatistics.computeScalar(values);
        vectorSummary = LatencyStatistics.computeVector(values);
        BenchmarkResult benchmarkResult = LatencyStatistics.benchmark(values);
        scalarBenchmarkMillis = benchmarkResult.scalarMillis();
        vectorBenchmarkMillis = benchmarkResult.vectorMillis();
    }

    public long errorCount() {
        return errorCount;
    }

    public Set<String> errorIps() {
        return Collections.unmodifiableSet(errorIps);
    }

    public LatencySummary scalarSummary() {
        return scalarSummary;
    }

    public LatencySummary vectorSummary() {
        return vectorSummary;
    }

    public double scalarBenchmarkMillis() {
        return scalarBenchmarkMillis;
    }

    public double vectorBenchmarkMillis() {
        return vectorBenchmarkMillis;
    }
}
