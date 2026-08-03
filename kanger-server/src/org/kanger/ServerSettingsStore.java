/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Thread-safe server configuration store.
 *
 * <p>Reads are side-effect free. Mutations replace the complete properties
 * file through a same-directory temporary file and an atomic move when the
 * filesystem supports it.</p>
 */
final class ServerSettingsStore {

    private final Path file;
    private final Properties values = new Properties();

    ServerSettingsStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file.toAbsolutePath().normalize();
    }

    synchronized String get(String key, String defaultValue) {
        requireKey(key);
        return values.containsKey(key) ? values.getProperty(key) : defaultValue;
    }

    synchronized void set(String key, String value) throws IOException {
        requireKey(key);
        if (value == null) {
            values.remove(key);
        } else {
            values.setProperty(key, value);
        }
        persist();
    }

    synchronized List<String> getByPrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix must not be null");
        }
        List<String> keys = new ArrayList<String>();
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        Collections.sort(keys);

        List<String> result = new ArrayList<String>(keys.size());
        for (String key : keys) {
            result.add(values.getProperty(key));
        }
        return result;
    }

    synchronized void reload() throws IOException {
        Properties loaded = new Properties();
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(
                    file, StandardCharsets.UTF_8)) {
                loaded.load(reader);
            }
        }
        values.clear();
        values.putAll(loaded);
    }

    synchronized int size() {
        return values.size();
    }

    Path getFile() {
        return file;
    }

    private void persist() throws IOException {
        Path parent = file.getParent();
        if (parent == null) {
            throw new IOException("Settings file has no parent directory: " + file);
        }
        Files.createDirectories(parent);

        String prefix = file.getFileName().toString() + ".";
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "KANGER Server settings");
            }
            try {
                Files.move(temporary, file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("settings key must not be empty");
        }
    }
}
