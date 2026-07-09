package com.loghog;

import java.util.HashSet;
import java.util.Set;

public final class BatchMetrics {

    private long errorCount;
    private final Set<String> errorIps = new HashSet<>();
    private final IntArrayBuilder checkoutLatencies = new IntArrayBuilder();

    public void incrementErrorCount() {
        errorCount++;
    }

    public void addErrorIp(String ipAddress) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            errorIps.add(ipAddress);
        }
    }

    public void addCheckoutLatency(int latencyMs) {
        checkoutLatencies.add(latencyMs);
    }

    long errorCount() {
        return errorCount;
    }

    Set<String> errorIps() {
        return errorIps;
    }

    int[] checkoutLatencies() {
        return checkoutLatencies.toArray();
    }
}
