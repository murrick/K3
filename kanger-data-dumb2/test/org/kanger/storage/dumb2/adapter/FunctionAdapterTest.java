package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.FunctionBinding;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.Function;
import org.kanger.units.Term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Qualification of Function definition persistence without transient projections. */
public class FunctionAdapterTest {

    @Test
    void functionDefinitionRoundTripsWithLazyIdsAndFreshResultSlot() throws Exception {
        Mind mind = new Mind(new User());
        Term name = (Term) mind.getTerms().add("function-test");

        ArgumentsList inputs = new ArgumentsList();
        Argument term = new Argument();
        term.setPersistentReference(7001L, ArgumentType.TERM);
        term.setVarOrder(4);
        inputs.add(term);
        Argument nested = new Argument();
        nested.setPersistentReference(8002L, ArgumentType.FUNCTION);
        nested.setVarOrder(9);
        inputs.add(nested);

        Function source = mind.getFunctions().add(name, inputs);
        source.setBinding(FunctionBinding.UDF_DYNAMIC);
        source.getResult(); // prove the orchestration slot exists but is not persisted

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                FunctionAdapter.TYPE_NAME, FunctionAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        assertEquals(2, projected.asStruct().get("arguments").asList().size());

        byte[] bytes = StructuralValueCodec.encode(definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(definition.getDescriptor(), bytes);
        IUnit restoredUnit = adapters.materialize(definition, decoded, mind);
        Function restored = (Function) restoredUnit;

        assertSame(FunctionAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getMindId(), restored.getMindId());
        assertEquals(source.getNameId(), restored.getNameId());
        assertEquals(2, restored.getRange());
        assertEquals(FunctionBinding.UDF_DYNAMIC, restored.getBinding());
        assertEquals(3, restored.getArguments().size());

        Argument restoredTerm = (Argument) restored.getArguments().get(0);
        assertEquals(ArgumentType.TERM, restoredTerm.getType());
        assertEquals(7001L, restoredTerm.getId());
        assertEquals(4, restoredTerm.getVarOrder());

        Argument restoredNested = (Argument) restored.getArguments().get(1);
        assertEquals(ArgumentType.FUNCTION, restoredNested.getType());
        assertEquals(8002L, restoredNested.getId());
        assertEquals(9, restoredNested.getVarOrder());

        Argument result = (Argument) restored.getArguments().get(2);
        assertEquals(ArgumentType.EMPTY, result.getType());
        assertEquals(-1L, result.getId());
    }

    @Test
    void functionNameAndArgumentsDoNotNeedResolutionDuringMaterialization() throws Exception {
        Mind mind = new Mind(new User());
        Function source = new Function(mind);
        source.setId(41L);
        source.setMindId(mind.getId());
        source.setPersistentNameId(9001L);
        source.setRange(1);
        source.setBinding(FunctionBinding.INFRASTRUCTURE);
        Argument input = new Argument();
        input.setPersistentReference(9002L, ArgumentType.TVARIABLE);
        source.getArguments().add(input);
        source.getArguments().add(new Argument());

        StructuralValue projected = FunctionAdapter.INSTANCE.project(source, mind);
        Function restored = FunctionAdapter.INSTANCE.materialize(projected, mind);

        assertEquals(9001L, restored.getNameId());
        Argument restoredInput = (Argument) restored.getArguments().get(0);
        assertEquals(ArgumentType.TVARIABLE, restoredInput.getType());
        assertEquals(9002L, restoredInput.getId());
    }
}
