package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.FValue;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Function;
import org.kanger.units.Term;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Qualification of FValue across the neutral descriptor boundary. */
public class FValueAdapterTest {

    @Test
    void fValueRoundTripsThroughDescriptorBytes() throws Exception {
        Mind mind = new Mind(new User());

        Term functionName = (Term) mind.getTerms().add("fvalue-test");
        Function function = mind.getFunctions().add(functionName, new ArgumentsList());

        Term term = (Term) mind.getTerms().add("result");

        FValue source = new FValue();
        source.setMind(mind);
        source.setId(47L);
        source.setMindId(mind.getId());
        source.setFunction(function);
        source.setValue(term);
        source.setStamp(Arrays.asList(0L, term.getId(), 91L));

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                FValueAdapter.TYPE_NAME, FValueAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        byte[] bytes = StructuralValueCodec.encode(definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(definition.getDescriptor(), bytes);
        IUnit restored = adapters.materialize(definition, decoded, mind);

        assertSame(FValueAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getMindId(), restored.getMindId());
        FValue result = (FValue) restored;
        assertEquals(source.getFunctionId(), result.getFunctionId());
        assertEquals(source.getValueId(), result.getValueId());
        assertEquals(source.getStamp(), result.getStamp());
        assertEquals(source.getHash(), result.getHash());
        assertSame(function, result.getFunction());
        assertSame(term, result.getValue(mind));
    }

    @Test
    void descriptorKeepsStampAsRawOrderedInt64List() {
        assertEquals("FValue-v1", FValueAdapter.INSTANCE.getDescriptor().getName());
        assertEquals("functions", FValueAdapter.INSTANCE.getDescriptor()
                .getFields().get(3).getDescriptor().getTarget());
        assertEquals("dictionary", FValueAdapter.INSTANCE.getDescriptor()
                .getFields().get(4).getDescriptor().getTarget());
        assertEquals(org.kanger.storage.dumb2.descriptor.Descriptor.Kind.INT64,
                FValueAdapter.INSTANCE.getDescriptor().getFields().get(5)
                        .getDescriptor().getElement().getKind());
    }
}
