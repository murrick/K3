package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.ICause;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.Rule;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of Rule-v2 without eager Domain/Term/Rule hydration. */
public class RuleAdapterTest {

    @Test
    void ruleRoundTripsAsPersistentIdGraph() throws Exception {
        Mind mind = new Mind(new User());

        Rule source = new Rule(mind);
        source.setId(81L);
        source.setMindId(mind.getId());
        source.setPersistentOriginId(9001L);
        source.setVarIndex(12);
        source.setPersistentFlags(true, true, true, true, false);
        source.setPersistentTreeIds(Arrays.asList(
                Arrays.asList(9101L, 9102L),
                Arrays.asList(9103L)));
        source.getPredicates().add(9201L);
        source.getPredicates().add(9202L);
        source.getTerms().add(9001L);
        source.getTerms().add(9301L);

        Solve donor = new Solve();
        donor.setPersistentPredicateId(9201L);
        donor.setRange(1);
        donor.setAntc(false);
        Argument donorArgument = new Argument();
        donorArgument.setPersistentReference(9301L, ArgumentType.TERM);
        donorArgument.setVarOrder(6);
        donor.getArguments().add(donorArgument);

        Cause cause = new Cause();
        cause.setPersistentState(9401L, donor);
        source.getCauses().add(cause);

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                RuleAdapter.TYPE_NAME, RuleAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        byte[] bytes = StructuralValueCodec.encode(definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(definition.getDescriptor(), bytes);
        IUnit restoredUnit = adapters.materialize(definition, decoded, mind);
        Rule restored = (Rule) restoredUnit;

        assertSame(RuleAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(81L, restored.getId());
        assertEquals(mind.getId(), restored.getMindId());
        assertEquals(9001L, restored.getOriginId());
        assertEquals(12, restored.getVarIndex());
        assertTrue(restored.isQuery());
        assertTrue(restored.isGenerated());
        assertTrue(restored.isStored());
        assertTrue(restored.isSubstitutable());
        assertEquals(false, restored.isAbstractive());

        List<List<Long>> tree = restored.getPersistentTreeIds();
        assertEquals(Arrays.asList(
                Arrays.asList(9101L, 9102L),
                Arrays.asList(9103L)), tree);

        assertEquals(
                new java.util.HashSet<Long>(Arrays.asList(9201L, 9202L)),
                restored.getPredicates());
        assertEquals(
                new java.util.HashSet<Long>(Arrays.asList(9001L, 9301L)),
                restored.getTerms());

        assertEquals(1, restored.getCauses().size());
        Cause restoredCause = (Cause) restored.getCauses().iterator().next();
        assertEquals(9401L, restoredCause.getRuleId());
        assertEquals(9201L, restoredCause.getDonor().getPredicateId());
        Argument restoredArgument =
                (Argument) restoredCause.getDonor().getArguments().get(0);
        assertEquals(ArgumentType.TERM, restoredArgument.getType());
        assertEquals(9301L, restoredArgument.getId());
        assertEquals(6, restoredArgument.getVarOrder());
    }

    @Test
    void projectionReadsLazyTreeIdsWithoutDomainResolution() throws Exception {
        Mind mind = new Mind(new User());
        Rule source = new Rule(mind);
        source.setId(82L);
        source.setMindId(mind.getId());
        source.setPersistentOriginId(9501L);
        source.setPersistentTreeIds(Arrays.asList(
                Arrays.asList(Long.MAX_VALUE, Long.MAX_VALUE - 1)));
        source.getPredicates().add(777L);
        source.getTerms().add(888L);

        StructuralValue projected = RuleAdapter.INSTANCE.project(source, mind);
        StructuralValue tree = projected.asStruct().get("tree");

        assertEquals(1, tree.asList().size());
        assertEquals(Long.MAX_VALUE,
                tree.asList().get(0).asList().get(0).getReferenceId());
        assertEquals(Long.MAX_VALUE - 1,
                tree.asList().get(0).asList().get(1).getReferenceId());
        assertEquals(777L,
                projected.asStruct().get("predicateIndex").asList()
                        .get(0).getReferenceId());
        assertEquals(888L,
                projected.asStruct().get("termIndex").asList()
                        .get(0).getReferenceId());
    }

    @Test
    void causesAreProjectedInCanonicalOrder() throws Exception {
        Mind mind = new Mind(new User());
        Rule source = new Rule(mind);
        source.setId(83L);
        source.setMindId(mind.getId());
        source.setPersistentOriginId(9601L);
        source.setPersistentTreeIds(Arrays.<List<Long>>asList());

        source.getCauses().add(cause(22L, 300L));
        source.getCauses().add(cause(11L, 400L));

        StructuralValue projected = RuleAdapter.INSTANCE.project(source, mind);
        List<StructuralValue> causes =
                projected.asStruct().get("causes").asList();

        assertEquals(11L, causes.get(0).asStruct().get("rule").getReferenceId());
        assertEquals(22L, causes.get(1).asStruct().get("rule").getReferenceId());
    }

    private static Cause cause(long ruleId, long predicateId) {
        Solve donor = new Solve();
        donor.setPersistentPredicateId(predicateId);
        donor.setRange(0);
        donor.setAntc(true);
        Cause cause = new Cause();
        cause.setPersistentState(ruleId, donor);
        return cause;
    }
}
