package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Context lifecycle proof that type definitions survive close/open. */
public class ContextManifestLifecycleTest {

    @TempDir
    Path root;

    @Test
    void registeredTypeIsPublishedBeforeContextReopen() throws Exception {
        Path location = root.resolve("manifest-lifecycle");
        UUID contextId;
        int typeCode;

        try (ContextStore store = ContextStore.create(location)) {
            contextId = store.getContextId();
            TypeDefinition definition = store.registerType("TVALUE",
                    Descriptor.struct("TValue-v1", Collections.singletonList(
                            Descriptor.field("value", Descriptor.INT64))));
            typeCode = definition.getTypeCode();

            ContextManifestStore.Manifest onDisk =
                    ContextManifestStore.read(ContextStore.contextPath(location));
            assertEquals(definition,
                    onDisk.getTypeRegistry().resolve(typeCode));
        }

        try (ContextStore reopened = ContextStore.open(location)) {
            assertEquals(contextId, reopened.getContextId());
            assertEquals("TVALUE", reopened.resolveType(typeCode).getTypeName());
            assertEquals("TValue-v1",
                    reopened.resolveType(typeCode).getDescriptor().getName());
        }
    }
}
