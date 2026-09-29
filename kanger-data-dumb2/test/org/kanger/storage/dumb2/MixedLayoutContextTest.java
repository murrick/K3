package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IBase;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.Step;
import org.kanger.storage.dumb2.adapter.RuleAdapter;
import org.kanger.storage.dumb2.adapter.RuleV1Adapter;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.units.Domain;
import org.kanger.units.Predicate;
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 qualification for lazy mixed physical layouts and explicit
 * canonicalization through reindex.
 */
public class MixedLayoutContextTest {

    @TempDir
    Path root;

    @Test
    void ruleV1AndRuleV2CoexistAndDecodeByRecordTypeCode() throws Exception {
        Fixture fixture = fixture();
        Path location = root.resolve("mixed");

        ContextStore store = ContextStore.create(location);
        try {
            ContextBase base = (ContextBase) store.getBase("rules");

            ContextStep oldStep = historicalStep(
                    store, base, fixture.oldRule, -1L, 1001);
            base.add(oldStep);

            Step currentStep = new Step();
            currentStep.setId(fixture.currentRule.getId());
            currentStep.setHash(1002);
            currentStep.setData(fixture.currentRule);
            currentStep.setNext(oldStep);
            base.add(currentStep);

            assertEquals(1L, store.flush());
        } finally {
            store.close();
        }

        ContextStore reopened = ContextStore.open(location);
        try {
            ContextBase base = (ContextBase) reopened.getBase("rules");

            ContextStep oldStored =
                    (ContextStep) base.get(fixture.oldRule.getId());
            ContextStep currentStored =
                    (ContextStep) base.get(fixture.currentRule.getId());

            TypeDefinition oldType = reopened.resolveType(
                    oldStored.getPersistentRecord().getTypeCode());
            TypeDefinition currentType = reopened.resolveType(
                    currentStored.getPersistentRecord().getTypeCode());

            assertEquals("RULE", oldType.getTypeName());
            assertEquals("Rule-v1", oldType.getDescriptor().getName());
            assertEquals("RULE", currentType.getTypeName());
            assertEquals("Rule-v2", currentType.getDescriptor().getName());
            assertTrue(oldType.getTypeCode() != currentType.getTypeCode());

            assertNull(oldStored.getData());
            assertNull(currentStored.getData());

            Rule oldRestored = (Rule) oldStored.getData(fixture.mind);
            Rule currentRestored =
                    (Rule) currentStored.getData(fixture.mind);

            assertEquals(fixture.oldRule.getPersistentTreeIds(),
                    oldRestored.getPersistentTreeIds());
            assertEquals(fixture.currentRule.getPersistentTreeIds(),
                    currentRestored.getPersistentTreeIds());

            // V1 had no stored acceleration indexes; it can derive them lazily.
            assertTrue(oldRestored.getPredicates().isEmpty());
            assertTrue(oldRestored.getTerms().isEmpty());
            oldRestored.ensurePersistentReferenceIndexes(fixture.mind);
            assertRuleIndexes(oldRestored, fixture.oldPredicate,
                    fixture.oldPayload, fixture.origin);

            // V2 carries the same semantic reference indexes immediately.
            assertRuleIndexes(currentRestored, fixture.currentPredicate,
                    fixture.currentPayload, fixture.origin);
        } finally {
            reopened.close();
        }
    }

    @Test
    void reindexCanonicalizesRuleV1ToRuleV2WithoutSemanticChange()
            throws Exception {
        Fixture fixture = fixture();
        Path sourceLocation = root.resolve("source");
        Path targetLocation = root.resolve("target");

        ContextStore source = ContextStore.create(sourceLocation);
        ContextStore target = ContextStore.create(targetLocation);
        try {
            ContextBase sourceBase =
                    (ContextBase) source.getBase("rules");
            sourceBase.add(historicalStep(
                    source, sourceBase, fixture.oldRule, -1L, 2001));
            source.flush();

            ContextBase targetBase =
                    (ContextBase) target.getBase("rules");
            sourceBase.reindex(targetBase, fixture.mind);
            target.flush();

            ContextStep migrated =
                    (ContextStep) targetBase.get(fixture.oldRule.getId());
            TypeDefinition targetType = target.resolveType(
                    migrated.getPersistentRecord().getTypeCode());

            assertEquals("RULE", targetType.getTypeName());
            assertEquals("Rule-v2", targetType.getDescriptor().getName(),
                    "reindex must write the current canonical layout");

            Rule restored = (Rule) migrated.getData(fixture.mind);
            assertEquals(fixture.oldRule.getId(), restored.getId());
            assertEquals(fixture.oldRule.getOriginId(), restored.getOriginId());
            assertEquals(fixture.oldRule.getPersistentTreeIds(),
                    restored.getPersistentTreeIds());
            assertRuleIndexes(restored, fixture.oldPredicate,
                    fixture.oldPayload, fixture.origin);
        } finally {
            target.close();
            source.close();
        }
    }

    private static ContextStep historicalStep(
            ContextStore store,
            ContextBase base,
            Rule rule,
            long nextId,
            int hash) throws Exception {
        TypeDefinition definition = store.registerType(
                RuleV1Adapter.TYPE_NAME,
                RuleV1Adapter.INSTANCE.getDescriptor());
        StructuralValue structural =
                RuleV1Adapter.INSTANCE.project(rule, rule.getMind());
        byte[] payload = StructuralValueCodec.encode(
                definition.getDescriptor(), structural);
        PersistentRecord record = new PersistentRecord(
                rule.getId(), hash, nextId,
                definition.getTypeCode(), payload);
        return new ContextStep(base, record);
    }

    private static void assertRuleIndexes(
            Rule rule,
            Predicate predicate,
            Term payload,
            Term origin) {
        assertEquals(Collections.singleton(predicate.getId()),
                rule.getPredicates());
        assertEquals(
                new HashSet<Long>(Arrays.asList(
                        origin.getId(),
                        predicate.getNameId(),
                        payload.getId())),
                rule.getTerms());
    }

    private static Fixture fixture() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);

        Term origin = (Term) mind.getTerms().add("mixed-origin");

        Rule oldRule = new Rule(mind);
        oldRule.setId(100L);
        oldRule.setMindId(mind.getId());
        oldRule.setPersistentOriginId(origin.getId());
        oldRule.setVarIndex(1);
        oldRule.setPersistentFlags(
                false, false, true, false, false);

        Term oldPredicateName =
                (Term) mind.getTerms().add("mixed-old-predicate");
        Term oldPayload =
                (Term) mind.getTerms().add("mixed-old-payload");
        Predicate oldPredicate =
                mind.getPredicates().add(oldPredicateName, 1);
        ArgumentsList oldArguments = new ArgumentsList();
        oldArguments.add(new Argument((org.kanger.interfaces.ITerm) oldPayload));
        Domain oldDomain = mind.getDomains().add(
                oldPredicate, false, oldArguments, oldRule);
        oldRule.setPersistentTreeIds(Collections.singletonList(
                Collections.singletonList(oldDomain.getId())));

        Rule currentRule = new Rule(mind);
        currentRule.setId(101L);
        currentRule.setMindId(mind.getId());
        currentRule.setPersistentOriginId(origin.getId());
        currentRule.setVarIndex(2);
        currentRule.setPersistentFlags(
                false, false, true, false, false);

        Term currentPredicateName =
                (Term) mind.getTerms().add("mixed-current-predicate");
        Term currentPayload =
                (Term) mind.getTerms().add("mixed-current-payload");
        Predicate currentPredicate =
                mind.getPredicates().add(currentPredicateName, 1);
        ArgumentsList currentArguments = new ArgumentsList();
        currentArguments.add(new Argument((org.kanger.interfaces.ITerm) currentPayload));
        Domain currentDomain = mind.getDomains().add(
                currentPredicate, false, currentArguments, currentRule);
        currentRule.setPersistentTreeIds(Collections.singletonList(
                Collections.singletonList(currentDomain.getId())));

        return new Fixture(
                mind, origin,
                oldRule, oldPredicate, oldPayload,
                currentRule, currentPredicate, currentPayload);
    }

    private static final class Fixture {
        private final Mind mind;
        private final Term origin;
        private final Rule oldRule;
        private final Predicate oldPredicate;
        private final Term oldPayload;
        private final Rule currentRule;
        private final Predicate currentPredicate;
        private final Term currentPayload;

        private Fixture(Mind mind,
                        Term origin,
                        Rule oldRule,
                        Predicate oldPredicate,
                        Term oldPayload,
                        Rule currentRule,
                        Predicate currentPredicate,
                        Term currentPayload) {
            this.mind = mind;
            this.origin = origin;
            this.oldRule = oldRule;
            this.oldPredicate = oldPredicate;
            this.oldPayload = oldPayload;
            this.currentRule = currentRule;
            this.currentPredicate = currentPredicate;
            this.currentPayload = currentPayload;
        }
    }
}
