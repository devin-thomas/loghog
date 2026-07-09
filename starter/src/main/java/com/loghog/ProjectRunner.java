package com.loghog;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ProjectRunner {

    private static final int BATCH_SIZE = 1000;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java --add-modules jdk.incubator.vector -jar <your-jar-file> <logFilePath>");
            System.exit(1);
        }

        Path logFile = Paths.get(args[0]);

        if (!Files.exists(logFile)) {
            System.err.println("Error: File not found at " + logFile);
            System.exit(1);
        }

        LogMetricsParser metricsParser = new LogMetricsParser();
        Map<Pattern, Method> extractors = discoverExtractors(LogMetricsParser.class);
        long startTime = System.nanoTime();
        AnalysisResult result = analyze(logFile, metricsParser, extractors);

        System.out.println("--- LogHog Analysis Complete ---");
        System.out.println("Total ERROR logs found: " + result.errorCount());
        System.out.println("Unique IP addresses causing errors: " + new TreeSet<>(result.errorIps()));
        System.out.println("Average latency for /checkout (Scalar): " + formatDouble(result.scalarSummary().mean()) + " ms");
        System.out.println("Standard deviation for /checkout (Scalar): " + formatDouble(result.scalarSummary().stdDev()) + " ms");
        System.out.println("Average latency for /checkout (Vector API): " + formatDouble(result.vectorSummary().mean()) + " ms");
        System.out.println("Standard deviation for /checkout (Vector API): " + formatDouble(result.vectorSummary().stdDev()) + " ms");
        System.out.println("Scalar stats benchmark: " + formatDouble(result.scalarBenchmarkMillis()) + " ms");
        System.out.println("Vector stats benchmark: " + formatDouble(result.vectorBenchmarkMillis()) + " ms");

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        System.out.println("\nTotal execution time: " + durationMs + " ms");
    }

    private static AnalysisResult analyze(Path logFile, LogMetricsParser parser, Map<Pattern, Method> extractors) throws IOException {
        AnalysisResult result = new AnalysisResult();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             BadDataLogger badDataLogger = new BadDataLogger(Paths.get("bad_data.log"));
             Stream<String> lines = Files.lines(logFile)) {

            List<CompletableFuture<BatchMetrics>> futures = new ArrayList<>();
            List<String> batch = new ArrayList<>(BATCH_SIZE);

            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                batch.add(iterator.next());
                if (batch.size() == BATCH_SIZE) {
                    futures.add(submitBatch(executor, List.copyOf(batch), parser, extractors, badDataLogger));
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                futures.add(submitBatch(executor, List.copyOf(batch), parser, extractors, badDataLogger));
            }

            for (CompletableFuture<BatchMetrics> future : futures) {
                try {
                    result.merge(future.join());
                } catch (CompletionException e) {
                    System.err.println("Skipping failed batch: " + String.valueOf(e.getCause()));
                }
            }
        }
        result.finishStatistics();
        return result;
    }

    private static CompletableFuture<BatchMetrics> submitBatch(
            ExecutorService executor,
            List<String> batch,
            LogMetricsParser parser,
            Map<Pattern, Method> extractors,
            BadDataLogger badDataLogger) {
        return CompletableFuture.supplyAsync(() -> processBatch(batch, parser, extractors, badDataLogger), executor);
    }

    private static BatchMetrics processBatch(
            List<String> lines,
            LogMetricsParser parser,
            Map<Pattern, Method> extractors,
            BadDataLogger badDataLogger) {
        BatchMetrics batchMetrics = new BatchMetrics();
        for (String rawLine : lines) {
            try {
                Optional<LogEntry> entry = LogParser.parse(rawLine);
                if (entry.isEmpty()) {
                    badDataLogger.log(rawLine);
                    continue;
                }

                for (Map.Entry<Pattern, Method> extractor : extractors.entrySet()) {
                    if (extractor.getKey().matcher(rawLine).find()) {
                        invokeExtractor(parser, extractor.getValue(), entry.get(), batchMetrics, rawLine, badDataLogger);
                    }
                }
            } catch (RuntimeException e) {
                badDataLogger.log(rawLine);
            }
        }
        return batchMetrics;
    }

    private static void invokeExtractor(
            LogMetricsParser parser,
            Method method,
            LogEntry entry,
            BatchMetrics batchMetrics,
            String rawLine,
            BadDataLogger badDataLogger) {
        try {
            method.invoke(parser, entry, batchMetrics);
        } catch (ReflectiveOperationException e) {
            badDataLogger.log("EXTRACTOR_FAILURE: " + rawLine);
        }
    }

    private static Map<Pattern, Method> discoverExtractors(Class<LogMetricsParser> parserClass) {
        Map<Pattern, Method> extractors = new LinkedHashMap<>();
        for (Method method : parserClass.getDeclaredMethods()) {
            Extractor extractor = method.getAnnotation(Extractor.class);
            if (extractor == null) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 2
                    || parameterTypes[0] != LogEntry.class
                    || parameterTypes[1] != BatchMetrics.class) {
                throw new IllegalStateException("Extractor methods must accept (LogEntry, BatchMetrics): " + method);
            }
            method.setAccessible(true);
            extractors.put(Pattern.compile(extractor.value()), method);
        }
        List<Map.Entry<Pattern, Method>> entries = new ArrayList<>(extractors.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<Pattern, Method> entry) -> entry.getKey().pattern()));
        Map<Pattern, Method> orderedExtractors = new LinkedHashMap<>();
        for (Map.Entry<Pattern, Method> entry : entries) {
            orderedExtractors.put(entry.getKey(), entry.getValue());
        }
        return orderedExtractors;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
