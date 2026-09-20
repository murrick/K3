package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Qualification of TVariable across the neutral descriptor boundary. */
public class TVariableAdapterTest {

    @Test
    void tVariableRoundTripsThroughDescriptorBytes() throws Exception {
        Mind mind = new Mind(new User());
        ITerm name = mind.getTerms().add("x");

        Rule rule = new Rule(mind);
        rule.setId(31L);
        rule.setMindId(mind.getId());
        mind.getRules().set(rule);

        TVariable source = new TVariable(mind);
        source.setId(47L);
        source.setMindId(mind.getId());
        source.setName(name);
        source.setIndex(5);
        source.setRule(rule);

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                TVariableAdapter.TYPE_NAME,
                TVariableAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        byte[] bytes = StructuralValueCodec.encode(
                definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(
                definition.getDescriptor(), bytes);
        IUnit restored = adapters.materialize(definition, decoded, mind);

        assertSame(TVariableAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getMindId(), restored.getMindId());
        assertEquals(source.getHash(), restored.getHash());

        TVariable variable = (TVariable) restored;
        assertEquals(source.getNameId(), variable.getNameId());
        assertEquals(source.getIndex(), variable.getIndex());
        assertEquals(source.getRuleId(), variable.getRuleId());
        assertSame(name, variable.getName(mind));
        assertSame(rule, variable.getRule(mind));
    }
}
