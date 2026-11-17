/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DurableFilesTest {
    @TempDir Path root;

    @Test void unsupportedDirectoryProviderMustRefuseInsteadOfAcknowledging() throws Exception {
        URI uri = URI.create("jar:" + root.resolve("image.zip").toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Collections.singletonMap("create", "true"))) {
            Path directory = Files.createDirectory(zip.getPath("/context"));
            assertThrows(IOException.class, () -> DurableFiles.forceDirectory(directory));
        }
    }

    @Test void snapshotTreeDoesNotFollowAFileLinkOutsideItsFrame() throws Exception {
        Path external = Files.write(root.resolve("external"), new byte[]{1, 2, 3});
        Path frame = Files.createDirectory(root.resolve("frame"));
        Files.createSymbolicLink(frame.resolve("base"), external);
        assertThrows(IOException.class, () -> DurableFiles.forceTree(frame));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(external));
    }
}
