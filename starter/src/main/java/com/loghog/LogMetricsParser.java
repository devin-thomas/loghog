package com.loghog;

/**
 * Contains the business logic for extracting metrics from logs.
 * Methods in this class should be annotated with @Extractor to be
 * dynamically discovered at startup.
 */
public class LogMetricsParser {

    @Extractor(".*ERROR.*")
    public void countErrors(LogEntry entry, BatchMetrics batchMetrics) {
        batchMetrics.incrementErrorCount();
        entry.ipAddress().ifPresent(batchMetrics::addErrorIp);
    }

    @Extractor(".*POST /checkout.*")
    public void measureLatency(LogEntry entry, BatchMetrics batchMetrics) {
        entry.latencyMs().ifPresent(batchMetrics::addCheckoutLatency);
    }
}
