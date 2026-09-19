package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.dumb2.adapter.TValueAdapter;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Independent read-direction proof: persisted typeCode and manifest are enough
 * to decode a payload structurally, without a KANGER type switch.
 */
public class ContextManifestGenericDecodeTest {

    @TempDir
    Path root;

    @Test
    void reopenedManifestDrivesGenericTValuePayloadDecode() throws Exception {
        Mind mind = new Mind(new User());
        ITerm variableName = mind.getTerms().add("x");
        ITerm donor = mind.getTerms().add("value");
        TVariable variable = mind.getTVars().createTVar(new Rule(mind), variableName);
        TValue source = mind.getTValues().add(variable, donor);

        StructuralValue projected = TValueAdapter.toStructural(source, mind);
        byte[] payload = StructuralValueCodec.encode(
                TValueAdapter.descriptor(), projected);

        Path location = root.resolve("generic-reader");
        int typeCode;
        try (ContextStore store = ContextStore.create(location)) {
            typeCode = store.registerType(
                    TValueAdapter.TYPE_NAME,
                    TValueAdapter.descriptor()).getTypeCode();
        }

        ContextManifestStore.Manifest manifest =
                ContextManifestStore.read(ContextStore.contextPath(location));
        TypeDefinition definition = manifest.getTypeRegistry().resolve(typeCode);

        // Generic reader boundary: from here down there is no TValue/UnitType switch.
        StructuralValue decoded = StructuralValueCodec.decode(
                definition.getDescriptor(), payload);
        Map<String, StructuralValue> fields = decoded.asStruct();

        assertEquals("TVALUE", definition.getTypeName());
        assertEquals(source.getId(), fields.get("id").asInt64());
        assertEquals(source.getMindId(), fields.get("mindId").asInt64());
        assertEquals(source.getValueId(), fields.get("value").getReferenceId());
        assertEquals("dictionary", fields.get("value").getReferenceSchema());
        assertEquals(source.getTVarId(), fields.get("variable").getReferenceId());
        assertEquals("tvariables", fields.get("variable").getReferenceSchema());
    }

    @Test
    void unknownPersistedTypeCodeCannotBeDecodedByGuessing() throws Exception {
        Path location = root.resolve("unknown-code");
        try (ContextStore ignored = ContextStore.create(location)) {
            // empty manifest is intentionally sufficient
        }

        ContextManifestStore.Manifest manifest =
                ContextManifestStore.read(ContextStore.contextPath(location));

        assertThrows(IllegalArgumentException.class,
                () -> manifest.getTypeRegistry().resolve(77));
    }
}
