package com.loghog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogParser {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) (?<level>INFO|WARN|ERROR): (?<message>.*)$");
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern LATENCY_PATTERN = Pattern.compile("latency_ms=(\\d+)");

    private LogParser() {
    }

    public static Optional<LogEntry> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = LOG_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            LocalDateTime timestamp = LocalDateTime.parse(matcher.group("timestamp"), TIMESTAMP_FORMATTER);
            LogEntry.LogLevel level = LogEntry.LogLevel.valueOf(matcher.group("level"));
            String message = matcher.group("message");
            Optional<String> ipAddress = findFirst(IP_PATTERN, line);
            Optional<Integer> latencyMs = findLatency(line);
            return Optional.of(new LogEntry(timestamp, level, message, ipAddress, latencyMs));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> findFirst(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
    }

    private static Optional<Integer> findLatency(String line) {
        Matcher matcher = LATENCY_PATTERN.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
