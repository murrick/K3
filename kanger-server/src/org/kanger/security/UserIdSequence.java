/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.UUID;

/**
 * Persistent monotonic user-id authority.
 *
 * <p>The next id is advanced before account workspace publication. Failed
 * creation may therefore skip an id, but deletion or failure can never make an
 * old persistent identity reusable.</p>
 */
final class UserIdSequence {

    private final Path file;

    UserIdSequence(Path credentialFile) {
        if (credentialFile == null) {
            throw new IllegalArgumentException("credential file must not be null");
        }
        this.file = credentialFile.toAbsolutePath().normalize()
                .resolveSibling("users.sequence");
    }

    long allocate(long minimum) throws IOException {
        if (minimum <= 0L) {
            throw new IllegalArgumentException("minimum user id must be positive");
        }
        long next = Math.max(read(), minimum);
        if (next == Long.MAX_VALUE) {
            throw new IOException("User id sequence exhausted");
        }
        write(next + 1L);
        return next;
    }

    void advanceBeyond(long userId) throws IOException {
        if (userId <= 0L || userId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("user id is outside sequence range");
        }
        long required = userId + 1L;
        if (read() < required) {
            write(required);
        }
    }

    long peek(long minimum) throws IOException {
        return Math.max(read(), minimum);
    }

    private long read() throws IOException {
        if (!Files.exists(file)) {
            return 1L;
        }
        String value = new String(
                Files.readAllBytes(file), StandardCharsets.US_ASCII).trim();
        if (value.isEmpty()) {
            throw new IOException("User id sequence is empty: " + file);
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0L) {
                throw new IOException("User id sequence is not positive: " + file);
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new IOException("Invalid user id sequence: " + file, error);
        }
    }

    private void write(long next) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName().toString()
                + ".tmp-" + UUID.randomUUID().toString());
        Files.write(temporary,
                Collections.singletonList(Long.toString(next)),
                StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        ownerOnly(temporary);
        try {
            Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        ownerOnly(file);
    }

    private static void ownerOnly(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException unsupported) {
            // Non-POSIX filesystem: rely on the enclosing service state ACL.
        }
    }
}
