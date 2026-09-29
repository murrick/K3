package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.enums.ArgumentType;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Qualification of embedded Cause-v1 persistence through ID-only references. */
public class CauseStructuralAdapterTest {

    @Test
    void causeRoundTripsWithoutResolvingRulePredicateOrArguments() throws Exception {
        Solve donor = new Solve();
        donor.setPersistentPredicateId(7101L);
        donor.setRange(1);
        donor.setAntc(false);

        Argument argument = new Argument();
        argument.setPersistentReference(7201L, ArgumentType.TERM);
        argument.setVarOrder(4);
        donor.getArguments().add(argument);

        Cause source = new Cause();
        source.setPersistentState(7001L, donor);

        StructuralValue projected = CauseStructuralAdapter.project(source);
        byte[] bytes = StructuralValueCodec.encode(
                CauseStructuralAdapter.DESCRIPTOR, projected);
        Cause restored = CauseStructuralAdapter.materialize(
                StructuralValueCodec.decode(CauseStructuralAdapter.DESCRIPTOR, bytes));

        assertEquals(7001L, restored.getRuleId());
        assertEquals(7101L, restored.getDonor().getPredicateId());
        assertEquals(1, restored.getDonor().getRange());
        assertEquals(false, restored.getDonor().isAntc());

        Argument restoredArgument =
                (Argument) restored.getDonor().getArguments().get(0);
        assertEquals(ArgumentType.TERM, restoredArgument.getType());
        assertEquals(7201L, restoredArgument.getId());
        assertEquals(4, restoredArgument.getVarOrder());
    }

    @Test
    void descriptorUsesTypedRuleReferenceAndEmbeddedSolve() {
        assertEquals("rules", CauseStructuralAdapter.DESCRIPTOR
                .getFields().get(0).getDescriptor().getTarget());
        assertEquals("Solve-v1", CauseStructuralAdapter.DESCRIPTOR
                .getFields().get(1).getDescriptor().getName());
    }
}
