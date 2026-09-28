package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.Step;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.units.Term;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration proof for the native self-describing DUMB2 record path.
 */
public class ContextSelfDescribingIntegrationTest {

    @TempDir
    Path root;

    @Test
    void semanticRecordReopensWithEnvelopeBeforeHydration() throws Exception {
        Mind mind = new Mind(new User());
        ITerm added = mind.getTerms().add("self-describing");
        Term source = (Term) added;

        Path location = root.resolve("semantic-reopen");
        int typeCode;
        long id = source.getId();
        int hash = source.getHash();

        ContextStore context = ContextStore.create(location);
        try {
            IBase base = context.getBase("dictionary");
            Step step = new Step();
            step.setId(id);
            step.setHash(hash);
            step.setData(source);
            step.setNext(null);
            base.add(step);

            assertEquals(1L, context.flush());

            ContextStep persisted = (ContextStep) base.get(id);
            typeCode = persisted.getPersistentRecord().getTypeCode();
            TypeDefinition definition = context.resolveType(typeCode);
            assertEquals("TERM", definition.getTypeName());

            // A semantic ContextStep carries the envelope but no object graph.
            assertNull(persisted.getData());
            assertEquals(id, persisted.getId());
            assertEquals(hash, persisted.getHash());
            assertEquals(-1L, persisted.getNextId());
        } finally {
            context.close();
        }

        ContextStore reopened = ContextStore.open(location);
        try {
            ContextStep stored =
                    (ContextStep) reopened.getBase("dictionary").get(id);

            assertEquals(id, stored.getId());
            assertEquals(hash, stored.getHash());
            assertEquals(-1L, stored.getNextId());
            assertEquals(typeCode, stored.getPersistentRecord().getTypeCode());
            assertNull(stored.getData(),
                    "semantic value must remain lazy after physical reopen");

            Term restored = (Term) stored.getData(mind);
            assertEquals(id, restored.getId());
            assertEquals(source.getType(), restored.getType());
            assertEquals(source.getValue(), restored.getValue());
        } finally {
            reopened.close();
        }
    }

    @Test
    void physicalLongPayloadsUseManifestTypesNotSemanticAdapters()
            throws Exception {
        Path location = root.resolve("physical-reopen");
        ContextStore context = ContextStore.create(location);
        try {
            IBase base = context.getBase("index");
            Step first = new Step();
            first.setId(0L);
            first.setHash(11);
            first.setData(Long.valueOf(42L));

            Step second = new Step();
            second.setId(1L);
            second.setHash(12);
            second.setData(java.util.Arrays.asList(
                    Long.valueOf(7L), Long.valueOf(8L)));
            second.setNext(first);

            base.add(first);
            base.add(second);
            context.flush();

            boolean hasLong = false;
            boolean hasLongs = false;
            for (TypeDefinition definition :
                    context.snapshotTypeRegistry().definitions()) {
                hasLong |= ContextRecordCodec.LONG_TYPE.equals(
                        definition.getTypeName());
                hasLongs |= ContextRecordCodec.LONGS_TYPE.equals(
                        definition.getTypeName());
            }
            assertTrue(hasLong);
            assertTrue(hasLongs);
            assertFalse(context.snapshotTypeRegistry().definitions().isEmpty());
        } finally {
            context.close();
        }

        ContextStore reopened = ContextStore.open(location);
        try {
            IBase base = reopened.getBase("index");
            IStep first = base.get(0L);
            IStep second = base.get(1L);

            // Neutral physical values need no Mind and can be attached eagerly.
            assertEquals(Long.valueOf(42L), first.getData());
            assertEquals(java.util.Arrays.asList(
                    Long.valueOf(7L), Long.valueOf(8L)), second.getData());
            assertEquals(1L, base.getRoot().getId());
            assertEquals(0L, base.getTop().getId());
        } finally {
            reopened.close();
        }
    }
}
