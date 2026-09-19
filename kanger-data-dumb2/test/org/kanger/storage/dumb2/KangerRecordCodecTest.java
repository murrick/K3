package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.dumb2.adapter.KangerAdapterRegistry;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * First physical proof that a DUMB2 record carries Context-local typeCode,
 * not UnitType.ordinal().
 */
public class KangerRecordCodecTest {

    @TempDir
    Path root;

    @Test
    void tvalueRecordUsesPublishedContextTypeCodeAndGenericPayload()
            throws Exception {
        Mind mind = new Mind(new User());
        ITerm variableName = mind.getTerms().add("x");
        ITerm donor = mind.getTerms().add("value");
        TVariable variable = mind.getTVars().createTVar(new Rule(mind), variableName);
        TValue source = mind.getTValues().add(variable, donor);

        Path location = root.resolve("record-codec");
        byte[] bytes;
        int typeCode;
        KangerRecordCodec codec =
                new KangerRecordCodec(new KangerAdapterRegistry());

        try (ContextStore context = ContextStore.create(location)) {
            // Deliberately consume code 1 so TVALUE identity is visibly
            // Context-local rather than derived from any runtime enum ordinal.
            context.registerType("PLACEHOLDER",
                    Descriptor.struct("Placeholder-v1",
                            Collections.singletonList(
                                    Descriptor.field("value", Descriptor.INT64))));

            bytes = codec.encode(
                    context, 91L, 0x11223344, -1L, source, mind);
            PersistentRecord physical = PersistentRecordCodec.decode(bytes);
            typeCode = physical.getTypeCode();

            TypeDefinition published = context.resolveType(typeCode);
            assertEquals("TVALUE", published.getTypeName());
            assertEquals(2, typeCode);
            assertNotEquals(UnitType.TVALUE.ordinal(), typeCode);
        }

        try (ContextStore reopened = ContextStore.open(location)) {
            KangerRecordCodec.DecodedRecord restored =
                    codec.decode(reopened, bytes, mind);

            assertEquals(91L, restored.getId());
            assertEquals(0x11223344, restored.getHash());
            assertEquals(-1L, restored.getNextId());
            assertEquals(typeCode, restored.getTypeCode());
            assertEquals("TVALUE", restored.getDefinition().getTypeName());
            assertEquals(source, restored.getUnit());
        }
    }
}
