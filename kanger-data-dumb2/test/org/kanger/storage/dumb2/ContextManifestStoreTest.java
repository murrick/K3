/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification for the self-describing DUMB 2.0 Context manifest. */
public class ContextManifestStoreTest {

    @TempDir
    Path root;

    @Test
    void createPersistsReopenableEmptyManifest() throws Exception {
        Path path = root.resolve("reopen.context");

        ContextManifestStore.Manifest created = ContextManifestStore.create(path);
        ContextManifestStore.Manifest reopened = ContextManifestStore.read(path);

        assertFalse(isZero(created.getContextId()));
        assertEquals(created.getContextId(), reopened.getContextId());
        assertEquals(0, reopened.getTypeRegistry().size());
    }

    @Test
    void manifestRoundTripsContextLocalTypeRegistry() throws Exception {
        Path path = root.resolve("typed.context");
        ContextManifestStore.Manifest created = ContextManifestStore.create(path);
        TypeRegistry registry = created.getTypeRegistry();
        TypeDefinition first = registry.register("TVALUE", oneField("TValue-v1"));
        TypeDefinition second = registry.register("RULE", oneField("Rule-v1"));

        ContextManifestStore.publish(path, created.getContextId(), registry);
        ContextManifestStore.Manifest reopened = ContextManifestStore.read(path);

        assertEquals(created.getContextId(), reopened.getContextId());
        assertEquals(first, reopened.getTypeRegistry().resolve(first.getTypeCode()));
        assertEquals(second, reopened.getTypeRegistry().resolve(second.getTypeCode()));
    }

    @Test
    void publishingRegistryNeverChangesContextId() throws Exception {
        Path path = root.resolve("stable.context");
        ContextManifestStore.Manifest created = ContextManifestStore.create(path);
        UUID before = created.getContextId();
        created.getTypeRegistry().register("TVALUE", oneField("TValue-v1"));

        ContextManifestStore.publish(path, before, created.getTypeRegistry());

        assertEquals(before, ContextManifestStore.read(path).getContextId());
    }

    @Test
    void movingManifestPreservesIdentityAndDescriptors() throws Exception {
        Path source = root.resolve("source.context");
        Path target = root.resolve("target.context");
        ContextManifestStore.Manifest created = ContextManifestStore.create(source);
        TypeDefinition definition = created.getTypeRegistry()
                .register("TVALUE", oneField("TValue-v1"));
        ContextManifestStore.publish(
                source, created.getContextId(), created.getTypeRegistry());

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        ContextManifestStore.Manifest moved = ContextManifestStore.read(target);

        assertEquals(created.getContextId(), moved.getContextId());
        assertEquals(definition,
                moved.getTypeRegistry().resolve(definition.getTypeCode()));
    }

    @Test
    void createNeverSilentlyReplacesExistingManifest() throws Exception {
        Path path = root.resolve("existing.context");
        UUID original = ContextManifestStore.create(path).getContextId();

        assertThrows(FileAlreadyExistsException.class,
                () -> ContextManifestStore.create(path));
        assertEquals(original, ContextManifestStore.read(path).getContextId());
    }


    @Test
    void publishRejectsContextIdentityReplacement() throws Exception {
        Path path = root.resolve("identity-guard.context");
        ContextManifestStore.Manifest created = ContextManifestStore.create(path);

        assertThrows(StorageLifecycleException.class, () ->
                ContextManifestStore.publish(
                        path, UUID.randomUUID(), created.getTypeRegistry()));

        assertEquals(created.getContextId(),
                ContextManifestStore.read(path).getContextId());
    }

    @Test
    void publishRejectsRemovalOfPublishedDescriptor() throws Exception {
        Path path = root.resolve("append-only.context");
        ContextManifestStore.Manifest created = ContextManifestStore.create(path);
        TypeDefinition published = created.getTypeRegistry()
                .register("TVALUE", oneField("TValue-v1"));
        ContextManifestStore.publish(
                path, created.getContextId(), created.getTypeRegistry());

        TypeRegistry empty = new TypeRegistry();
        assertThrows(StorageLifecycleException.class, () ->
                ContextManifestStore.publish(path, created.getContextId(), empty));

        assertEquals(published,
                ContextManifestStore.read(path).getTypeRegistry()
                        .resolve(published.getTypeCode()));
    }

    @Test
    void damagedPayloadIsSemanticCorruption() throws Exception {
        Path path = root.resolve("damaged.context");
        ContextManifestStore.create(path);
        byte[] bytes = Files.readAllBytes(path);
        bytes[8] ^= 0x01;
        Files.write(path, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextManifestStore.read(path));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void independentManifestsReceiveDistinctIds() throws Exception {
        UUID first = ContextManifestStore.create(root.resolve("first.context"))
                .getContextId();
        UUID second = ContextManifestStore.create(root.resolve("second.context"))
                .getContextId();

        assertNotEquals(first, second);
    }

    private static Descriptor oneField(String name) {
        return Descriptor.struct(name, Collections.singletonList(
                Descriptor.field("value", Descriptor.INT64)));
    }

    private static boolean isZero(UUID id) {
        return id.getMostSignificantBits() == 0L
                && id.getLeastSignificantBits() == 0L;
    }
}
