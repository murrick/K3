package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.primitives.Argument;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.units.Term;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ArgumentStructuralAdapterTest {

    @Test
    void emptyArgumentUsesRealNullAndRoundTrips() throws Exception {
        Mind mind = new Mind();
        Argument source = new Argument();
        source.setVarOrder(7);

        StructuralValue projected = ArgumentStructuralAdapter.project(source);
        Map<String, StructuralValue> fields = projected.asStruct();
        assertEquals(StructuralValue.Kind.NULL, fields.get("value").getKind());

        byte[] bytes = StructuralValueCodec.encode(ArgumentStructuralAdapter.DESCRIPTOR, projected);
        StructuralValue decoded = StructuralValueCodec.decode(ArgumentStructuralAdapter.DESCRIPTOR, bytes);
        Argument restored = ArgumentStructuralAdapter.materialize(decoded, mind);

        assertEquals(ArgumentType.EMPTY, restored.getType());
        assertEquals(-1L, restored.getId());
        assertEquals(7, restored.getVarOrder());
    }

    @Test
    void termArgumentPreservesTypedReferenceAndIdentity() throws Exception {
        Mind mind = new Mind();
        Term term = (Term) mind.getTerms().add("argument-test");
        Argument source = new Argument(term);
        source.setVarOrder(3);

        StructuralValue projected = ArgumentStructuralAdapter.project(source);
        StructuralValue stored = projected.asStruct().get("value");
        assertEquals(StructuralValue.Kind.REF, stored.getKind());
        assertEquals("dictionary", stored.getReferenceSchema());
        assertEquals(term.getId(), stored.getReferenceId());

        byte[] bytes = StructuralValueCodec.encode(ArgumentStructuralAdapter.DESCRIPTOR, projected);
        Argument restored = ArgumentStructuralAdapter.materialize(
                StructuralValueCodec.decode(ArgumentStructuralAdapter.DESCRIPTOR, bytes), mind);

        assertEquals(ArgumentType.TERM, restored.getType());
        assertEquals(term.getId(), restored.getId());
        assertSame(term, restored.getObject(mind));
        assertEquals(3, restored.getVarOrder());
    }
}
