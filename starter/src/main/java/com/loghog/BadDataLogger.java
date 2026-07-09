package com.loghog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class BadDataLogger implements AutoCloseable {

    private final BufferedWriter writer;

    public BadDataLogger(Path outputPath) throws IOException {
        writer = Files.newBufferedWriter(
                outputPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    public synchronized void log(String line) {
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write bad data entry", e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        writer.flush();
        writer.close();
    }
}
