/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.test;

import org.kanger.Mind;
import org.kanger.enums.DataType;
import org.kanger.factory.DictionaryFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Additive stabilization characterization corpus.
 *
 * <p>The historical {@link KangerTest} corpus remains unchanged. This class
 * contains narrowly scoped tests that make recovered invariants executable
 * before implementation changes are opened.</p>
 */
public final class KangerStabilizationTest {

    private IMind mind;

    private KangerStabilizationTest(IMind mind) {
        this.mind = mind;
    }

    public static boolean test(IMind mind, String prefix) throws Exception {
        KangerStabilizationTest suite = new KangerStabilizationTest(mind);
        Map<String, Method> methods = new TreeMap<>();
        for (Method method : KangerStabilizationTest.class.getDeclaredMethods()) {
            if (method.getName().startsWith(prefix) && method.getParameterTypes().length == 0) {
                method.setAccessible(true);
                methods.put(method.getName(), method);
            }
        }

        int success = 0;
        int failures = 0;
        long start = System.currentTimeMillis();
        System.out.println("====================================================");
        System.out.println("S5A stabilization characterization tests");

        for (Map.Entry<String, Method> entry : methods.entrySet()) {
            System.out.println("Testing: " + entry.getKey());
            try {
                entry.getValue().invoke(suite);
                ++success;
                System.out.println("OK");
            } catch (InvocationTargetException error) {
                ++failures;
                Throwable cause = error.getCause() == null ? error : error.getCause();
                cause.printStackTrace(System.err);
            }
            System.out.println("----------------------------------------------------");
        }

        System.out.println("Stabilization Timing: " + ((System.currentTimeMillis() - start) / 1000.0));
        System.out.println("Stabilization Success: " + success);
        System.out.println("Stabilization Fails: " + failures);
        System.out.println("====================================================");
        return failures == 0;
    }

    private void resetWorkspace() throws Exception {
        mind = mind.clearWorkspace();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private Set<String> rows(String... names) throws Exception {
        Set<String> rows = new LinkedHashSet<>();
        for (Map<String, ITerm> row : mind.getValues()) {
            StringBuilder value = new StringBuilder();
            for (String name : names) {
                if (value.length() > 0) {
                    value.append('|');
                }
                ITerm term = row.get(name);
                value.append(name).append('=');
                value.append(term == null ? "<missing>" : String.valueOf(term.getValue()));
            }
            rows.add(value.toString());
        }
        return rows;
    }

    public void set_s5a_01_exact_arithmetic_rows() throws Exception {
        resetWorkspace();
        Boolean result = mind.query("?$x $y x=y*2, (y=4 || y=5);");
        require(Boolean.TRUE.equals(result), "Expected TRUE arithmetic query");

        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                "x=8.0|y=4.0",
                "x=10.0|y=5.0"
        ));
        Set<String> actual = rows("x", "y");
        require(expected.equals(actual), "Expected exact rows " + expected + ", actual " + actual);
    }

    public void set_s5a_02_runtime_id_commit_release() throws Exception {
        resetWorkspace();
        Mind parent = (Mind) mind;

        Mind committed = new Mind(parent);
        require(Boolean.TRUE.equals(committed.query("!committed(value);")), "Child insertion failed");
        IRule accepted = committed.getAcceptedRule();
        require(accepted != null, "Accepted child rule is missing");
        long committedId = accepted.getId();
        require(parent.getRules().get(committedId) == null, "Child rule leaked before commit");
        require(parent.commit(committed), "Child commit was rejected");

        IRule published = parent.getRules().get(committedId);
        require(published != null, "Committed rule is not addressable by its runtime id");
        require(((Rule) published).getMindId() == parent.getId(),
                "Committed rule was not rebound to the parent Mind");

        Mind discarded = new Mind(parent);
        require(Boolean.TRUE.equals(discarded.query("!discarded(value);")), "Discarded child insertion failed");
        IRule rejected = discarded.getAcceptedRule();
        require(rejected != null, "Discarded child accepted rule is missing");
        long discardedId = rejected.getId();
        parent.release(discarded);
        require(parent.getRules().get(discardedId) == null, "Released child rule remains published");

        mind = parent;
    }

    public void set_s5a_03_set_order_identity() throws Exception {
        resetWorkspace();
        DictionaryFactory dictionary = (DictionaryFactory) mind.getTerms();

        ITerm first = dictionary.add(new Object[]{1, "alpha"});
        ITerm reordered = dictionary.add(new Object[]{"alpha", 1});
        ITerm empty = dictionary.add(new Object[]{});

        require(first.getId() == reordered.getId(), "SET order changed canonical identity");
        require(first.getType() == DataType.SET, "Expected SET term");
        require(empty.getType() == DataType.SET, "Expected empty SET term");
        require(((java.util.Collection<?>) empty.getValue()).isEmpty(), "Expected empty SET value");
        require(((Term) first).equalsTo((Term) reordered), "SET structural equality is inconsistent");
    }

    public void set_s5a_04_alpha_equivalent_rule_identity() throws Exception {
        resetWorkspace();

        require(Boolean.TRUE.equals(mind.query("!@x @y pair(x,y) -> linked(x,y);")),
                "Initial quantified rule was not accepted");
        IRule canonical = mind.getAcceptedRule();
        require(canonical != null, "Initial quantified rule is missing");
        long canonicalId = canonical.getId();
        int canonicalSize = mind.getRules().size();

        Boolean duplicate = mind.query("!@left @right pair(left,right) -> linked(left,right);");
        require(duplicate == null, "Alpha-renamed rule was not classified as a duplicate");
        require(mind.getRules().size() == canonicalSize,
                "Alpha-renamed rule created a second canonical object");
        require(mind.getRules().get(canonicalId) != null,
                "Canonical rule disappeared after alpha-equivalent input");

        require(Boolean.TRUE.equals(mind.query("!@left @right pair(left,right) -> linked(right,left);")),
                "Structurally different quantified rule was not accepted");
        require(mind.getAcceptedRule() != null && mind.getAcceptedRule().getId() != canonicalId,
                "Different variable-use structure collapsed into the alpha-equivalent rule");
    }

    public void set_s5a_05_storage_id_reopen() throws Exception {
        resetWorkspace();
        final String storageName = "data/s5a-storage-id";

        try {
            if (mind.isStorageUsed()) {
                mind = mind.closeStorage();
            }
            if (mind.isStorageExists(storageName)) {
                mind = mind.removeStorage(storageName);
            }

            mind = mind.useStorage(storageName);
            mind = mind.clearWorkspace();

            require(Boolean.TRUE.equals(mind.query("!persistent(value);")),
                    "Persistent rule was not accepted");
            IRule accepted = mind.getAcceptedRule();
            require(accepted != null, "Persistent accepted rule is missing");
            long persistentId = accepted.getId();

            mind = mind.closeStorage();
            mind = mind.useStorage(storageName);

            IRule loaded = mind.getRules().get(persistentId);
            require(loaded != null, "Persistent id was not restored after storage reopen");
            require(Boolean.TRUE.equals(mind.query("?persistent(value);")),
                    "Reloaded persistent rule is not logically visible");

            require(Boolean.TRUE.equals(mind.query("!next(value);")),
                    "Post-reopen rule was not accepted");
            require(mind.getAcceptedRule() != null && mind.getAcceptedRule().getId() > persistentId,
                    "Persistent allocator did not advance after reopen");
        } finally {
            if (mind.isStorageUsed()) {
                mind = mind.closeStorage();
            }
            if (mind.isStorageExists(storageName)) {
                mind = mind.removeStorage(storageName);
            }
        }
    }

    public void set_s5a_06_sibling_canonicalization() throws Exception {
        resetWorkspace();
        Mind parent = (Mind) mind;
        Mind first = new Mind(parent);
        Mind second = new Mind(parent);

        require(Boolean.TRUE.equals(first.query("!shared(value);")), "First sibling insertion failed");
        require(Boolean.TRUE.equals(second.query("!shared(value);")), "Second sibling insertion failed");
        IRule firstRule = first.getAcceptedRule();
        IRule secondRule = second.getAcceptedRule();
        require(firstRule != null && secondRule != null, "Sibling accepted rules are missing");
        require(firstRule.getId() != secondRule.getId(),
                "Independent sibling deltas unexpectedly shared a pre-commit rule id");

        require(parent.commit(first), "First sibling commit failed");
        IRule canonical = parent.getRules().find(secondRule);
        require(canonical != null && canonical.getId() == firstRule.getId(),
                "Updated parent does not expose the first sibling as canonical");

        require(parent.commit(second), "Second sibling commit failed");
        IRule merged = parent.getRules().find(secondRule);
        require(merged != null && merged.getId() == firstRule.getId(),
                "Second sibling was not deduplicated to the canonical parent rule");
        require(parent.getRules().get(secondRule.getId()) == null,
                "Duplicate sibling rule remained addressable after merge and pack");

        mind = parent;
    }

    public void set_s5a_07_nested_commit_release() throws Exception {
        resetWorkspace();
        Mind parent = (Mind) mind;

        Mind rollbackLevel1 = new Mind(parent);
        require(Boolean.TRUE.equals(rollbackLevel1.query("!rollback_l1(value);")),
                "Rollback level 1 insertion failed");
        long rollbackLevel1Id = rollbackLevel1.getAcceptedRule().getId();

        Mind rollbackLevel2 = new Mind(rollbackLevel1);
        require(Boolean.TRUE.equals(rollbackLevel2.query("!rollback_l2(value);")),
                "Rollback level 2 insertion failed");
        long rollbackLevel2Id = rollbackLevel2.getAcceptedRule().getId();

        require(rollbackLevel1.commit(rollbackLevel2), "Nested child commit into level 1 failed");
        require(rollbackLevel1.getRules().get(rollbackLevel2Id) != null,
                "Nested child is not visible in its committed intermediate parent");
        require(parent.getRules().get(rollbackLevel1Id) == null
                        && parent.getRules().get(rollbackLevel2Id) == null,
                "Nested changes leaked into root before outer commit");

        parent.release(rollbackLevel1);
        require(parent.getRules().get(rollbackLevel1Id) == null
                        && parent.getRules().get(rollbackLevel2Id) == null,
                "Outer release did not discard the full nested delta");

        Mind commitLevel1 = new Mind(parent);
        require(Boolean.TRUE.equals(commitLevel1.query("!commit_l1(value);")),
                "Commit level 1 insertion failed");
        long commitLevel1Id = commitLevel1.getAcceptedRule().getId();

        Mind commitLevel2 = new Mind(commitLevel1);
        require(Boolean.TRUE.equals(commitLevel2.query("!commit_l2(value);")),
                "Commit level 2 insertion failed");
        long commitLevel2Id = commitLevel2.getAcceptedRule().getId();

        require(commitLevel1.commit(commitLevel2), "Nested child commit into level 1 failed");
        require(parent.commit(commitLevel1), "Outer commit into root failed");
        require(parent.getRules().get(commitLevel1Id) != null
                        && parent.getRules().get(commitLevel2Id) != null,
                "Nested commit chain did not publish the complete delta");

        mind = parent;
    }
}
