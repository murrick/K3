/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.admin;

import org.kanger.security.SecureTokens;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent bearer-token authority for the local operator plane.
 *
 * <p>The raw token is shared only by the server JVM and the local
 * {@code kanger-admin} client. It is never returned by the admin protocol or
 * written to logs.</p>
 */
public final class AdminTokenStore {

    private static final Object AUTHORITY_LOCK = new Object();
    private static final Set<PosixFilePermission> OWNER_ONLY =
            Collections.unmodifiableSet(EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));

    private final Path file;

    public AdminTokenStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("admin token file must not be null");
        }
        this.file = file.toAbsolutePath().normalize();
    }

    /**
     * Loads the current token or atomically creates a new 256-bit token.
     */
    public String loadOrCreate() throws IOException {
        synchronized (AUTHORITY_LOCK) {
            if (Files.exists(file)) {
                return readLocked();
            }

            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String token = SecureTokens.random256();
            Path temporary = file.resolveSibling(file.getFileName().toString()
                    + ".tmp-" + UUID.randomUUID().toString());
            Files.write(temporary,
                    Collections.singletonList(token),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            restrict(temporary);
            try {
                Files.move(temporary, file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            restrict(file);
            return token;
        }
    }

    /**
     * Loads an existing token without creating operator authority implicitly.
     */
    public String load() throws IOException {
        synchronized (AUTHORITY_LOCK) {
            if (!Files.isRegularFile(file)) {
                throw new IOException("KANGER admin token file does not exist");
            }
            return readLocked();
        }
    }

    public Path getFile() {
        return file;
    }

    private String readLocked() throws IOException {
        String token = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .trim();
        if (token.length() < 32 || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
            throw new IOException("KANGER admin token file is invalid");
        }
        restrict(file);
        return token;
    }

    private static void restrict(Path target) throws IOException {
        try {
            Files.setPosixFilePermissions(target, OWNER_ONLY);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX platforms rely on the service account's protected home.
        }
    }
}
