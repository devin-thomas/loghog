package com.loghog;

public record LatencySummary(double mean, double stdDev, long count) {
}
