package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.Domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Qualification of Domain-v1 without resolving Rule, Predicate or arguments. */
public class DomainAdapterTest {

    @Test
    void domainRoundTripsAsLazyIdGraph() throws Exception {
        Mind mind = new Mind(new User());

        Domain source = new Domain(mind);
        source.setId(71L);
        source.setMindId(mind.getId());
        source.setPersistentRuleId(8001L);
        source.setSubstitutable(true);
        source.setAbstractive(false);
        source.setPersistentPredicateId(8002L);
        source.setRange(2);
        source.setAntc(true);

        Argument term = new Argument();
        term.setPersistentReference(8101L, ArgumentType.TERM);
        term.setVarOrder(2);
        source.getArguments().add(term);

        Argument function = new Argument();
        function.setPersistentReference(8102L, ArgumentType.FUNCTION);
        function.setVarOrder(5);
        source.getArguments().add(function);

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                DomainAdapter.TYPE_NAME, DomainAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        byte[] bytes = StructuralValueCodec.encode(definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(definition.getDescriptor(), bytes);
        IUnit restoredUnit = adapters.materialize(definition, decoded, mind);
        Domain restored = (Domain) restoredUnit;

        assertSame(DomainAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(71L, restored.getId());
        assertEquals(mind.getId(), restored.getMindId());
        assertEquals(8001L, restored.getRuleId());
        assertEquals(true, restored.isSubstitutable());
        assertEquals(false, restored.isAbstractive());
        assertEquals(8002L, restored.getPredicateId());
        assertEquals(2, restored.getRange());
        assertEquals(true, restored.isAntc());

        Argument restoredTerm = (Argument) restored.getArguments().get(0);
        assertEquals(ArgumentType.TERM, restoredTerm.getType());
        assertEquals(8101L, restoredTerm.getId());
        assertEquals(2, restoredTerm.getVarOrder());

        Argument restoredFunction = (Argument) restored.getArguments().get(1);
        assertEquals(ArgumentType.FUNCTION, restoredFunction.getType());
        assertEquals(8102L, restoredFunction.getId());
        assertEquals(5, restoredFunction.getVarOrder());
    }

    @Test
    void descriptorEmbedsSolveWithoutJavaMetadata() {
        assertEquals("Domain-v1", DomainAdapter.INSTANCE.getDescriptor().getName());
        assertEquals("rules", DomainAdapter.INSTANCE.getDescriptor()
                .getFields().get(3).getDescriptor().getTarget());
        assertEquals("Solve-v1", DomainAdapter.INSTANCE.getDescriptor()
                .getFields().get(6).getDescriptor().getName());
    }
}
