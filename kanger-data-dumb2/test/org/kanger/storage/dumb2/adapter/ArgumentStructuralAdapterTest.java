package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.enums.ArgumentType;
import org.kanger.primitives.Argument;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArgumentStructuralAdapterTest {

    @Test
    void emptyArgumentUsesRealNullAndRoundTrips() throws Exception {
        Argument source = new Argument();
        source.setVarOrder(7);

        StructuralValue projected = ArgumentStructuralAdapter.project(source);
        Map<String, StructuralValue> fields = projected.asStruct();
        assertEquals(StructuralValue.Kind.NULL, fields.get("value").getKind());

        byte[] bytes = StructuralValueCodec.encode(ArgumentStructuralAdapter.DESCRIPTOR, projected);
        StructuralValue decoded = StructuralValueCodec.decode(ArgumentStructuralAdapter.DESCRIPTOR, bytes);
        Argument restored = ArgumentStructuralAdapter.materialize(decoded);

        assertEquals(ArgumentType.EMPTY, restored.getType());
        assertEquals(-1L, restored.getId());
        assertEquals(7, restored.getVarOrder());
    }

    @Test
    void everyTypedArgumentPreservesReferenceWithoutRuntimeResolution() throws Exception {
        assertTypedRoundTrip(ArgumentType.TERM, "dictionary");
        assertTypedRoundTrip(ArgumentType.FUNCTION, "functions");
        assertTypedRoundTrip(ArgumentType.TVARIABLE, "tvariables");
        assertTypedRoundTrip(ArgumentType.FVALUE, "fvalues");
        assertTypedRoundTrip(ArgumentType.TVALUE, "tvalues");
    }

    private static void assertTypedRoundTrip(ArgumentType type, String namespace) throws Exception {
        Argument source = new Argument();
        source.setPersistentReference(41L, type);
        source.setVarOrder(3);

        StructuralValue projected = ArgumentStructuralAdapter.project(source);
        StructuralValue stored = projected.asStruct().get("value");
        assertEquals(StructuralValue.Kind.REF, stored.getKind());
        assertEquals(namespace, stored.getReferenceSchema());
        assertEquals(41L, stored.getReferenceId());

        byte[] bytes = StructuralValueCodec.encode(ArgumentStructuralAdapter.DESCRIPTOR, projected);
        Argument restored = ArgumentStructuralAdapter.materialize(
                StructuralValueCodec.decode(ArgumentStructuralAdapter.DESCRIPTOR, bytes));

        assertEquals(type, restored.getType());
        assertEquals(41L, restored.getId());
        assertEquals(3, restored.getVarOrder());
    }
}
