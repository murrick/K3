package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.enums.ArgumentType;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Solve;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Qualification of embedded Solve-v1 persistence through ID-only references. */
public class SolveStructuralAdapterTest {

    @Test
    void solveRoundTripsWithoutResolvingPredicateOrArguments() throws Exception {
        Solve source = new Solve();
        source.setPersistentPredicateId(5001L);
        source.setRange(2);
        source.setAntc(false);

        Argument term = new Argument();
        term.setPersistentReference(6001L, ArgumentType.TERM);
        term.setVarOrder(3);
        source.getArguments().add(term);

        Argument variable = new Argument();
        variable.setPersistentReference(6002L, ArgumentType.TVARIABLE);
        variable.setVarOrder(7);
        source.getArguments().add(variable);

        StructuralValue projected = SolveStructuralAdapter.project(source);
        byte[] bytes = StructuralValueCodec.encode(
                SolveStructuralAdapter.DESCRIPTOR, projected);
        Solve restored = SolveStructuralAdapter.materialize(
                StructuralValueCodec.decode(SolveStructuralAdapter.DESCRIPTOR, bytes));

        assertEquals(5001L, restored.getPredicateId());
        assertEquals(2, restored.getRange());
        assertEquals(false, restored.isAntc());
        assertEquals(2, restored.getArguments().size());

        Argument restoredTerm = (Argument) restored.getArguments().get(0);
        assertEquals(ArgumentType.TERM, restoredTerm.getType());
        assertEquals(6001L, restoredTerm.getId());
        assertEquals(3, restoredTerm.getVarOrder());

        Argument restoredVariable = (Argument) restored.getArguments().get(1);
        assertEquals(ArgumentType.TVARIABLE, restoredVariable.getType());
        assertEquals(6002L, restoredVariable.getId());
        assertEquals(7, restoredVariable.getVarOrder());
    }

    @Test
    void descriptorUsesTypedPredicateReferenceAndArgumentList() {
        assertEquals("predicates", SolveStructuralAdapter.DESCRIPTOR
                .getFields().get(0).getDescriptor().getTarget());
        assertEquals("Argument-v1", SolveStructuralAdapter.DESCRIPTOR
                .getFields().get(3).getDescriptor().getElement().getName());
    }
}
