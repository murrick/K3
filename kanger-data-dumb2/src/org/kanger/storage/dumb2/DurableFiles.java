/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Necessary local persistence barriers. Successful calls alone do not qualify a device profile. */
final class DurableFiles {
    private DurableFiles() { }

    static void forceFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Durability requires a regular file: " + path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        } catch (UnsupportedOperationException failure) {
            throw new IOException("File durability barrier is unavailable: " + path, failure);
        }
    }

    static void forceDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Durability requires a directory: " + path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException failure) {
            throw new IOException("Directory durability barrier is unavailable: " + path, failure);
        }
    }

    /** Bottom-up: persist each entry before its parent's entry, including newly created ancestors. */
    static void forceParents(Path path) throws IOException {
        for (Path parent = path.toAbsolutePath().normalize().getParent(); parent != null; parent = parent.getParent())
            forceDirectory(parent);
    }

    static void forceTree(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) forceTree(entry);
            }
            forceDirectory(path);
        } else forceFile(path);
    }

    /** Own frame only; dependencies are exact references, not recursively acquired knowledge. */
    static void forceContextFrame(Path location, long revision) throws IOException {
        forceFile(ContextStore.contextPath(location));
        Path connections = ConnectionStore.path(location);
        if (Files.exists(connections, LinkOption.NOFOLLOW_LINKS)) forceFile(connections);
        if (revision > RevisionStore.INITIAL_REVISION) {
            forceTree(ContextStore.generationPath(location, revision));
            forceDirectory(ContextStore.stateRoot(location));
        }
        forceParents(ContextStore.contextPath(location));
    }
}
