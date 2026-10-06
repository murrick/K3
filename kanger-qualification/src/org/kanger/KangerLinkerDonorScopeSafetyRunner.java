/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.primitives.Cause;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/** Native gate for separate execution and donor scopes, including donor order. */
public final class KangerLinkerDonorScopeSafetyRunner {
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("linker-donor-scope-").toString());
        boolean reference = args.length == 1 && "--reference".equals(args[0]);
        for (boolean descending : new boolean[]{true, false}) {
            runCase("full", descending, reference, "full", "full", true);
            runCase("receiver-limited", descending, reference, "receiver", "receiver", false);
            if (!reference) {
                runCase("receiver-retained", descending, false, "receiver", "full", true);
                runCase("empty-retained", descending, false, "empty", "full", false);
            }
        }
        System.out.println("LINKER_DONOR_SCOPE_OK mode=" + (reference ? "reference" : "split") + " checks=" + checks);
    }

    private static void runCase(String name, boolean descending, boolean reference,
                                String executionScope, String donorScope,
                                boolean expectBindings) throws Exception {
        Mind base = new Mind(new User());
        require(base.compile("!seed(alpha); !seed(beta);"), "native donor base compiles");
        Mind child = new Mind(base);
        try {
            Rule receiver = (Rule) child.compileLine("!@x ~seed(x);", false, null);
            require(receiver != null && receiver.getTree().size() == 1
                    && receiver.getTree().get(0).size() == 1, "native single-domain receiver");
            Domain domain = receiver.getTree().get(0).get(0);
            List<IRule> full = new ArrayList<>();
            for (IRule rule : child.getRules()) if (!rule.isDeleted(child)) full.add(rule);
            Collections.sort(full, descending ? Linker::compareRuleIdsDescending : Linker::compareRuleIdsAscending);
            require(full.size() == 3, "two donors and one receiver in native active view");
            List<IRule> execution = scope(executionScope, full, receiver);
            List<IRule> donors = executionScope.equals(donorScope)
                    ? execution : scope(donorScope, full, receiver);
            Linker linker = new Linker(child);
            Method indexMethod = method("buildDomainIndex", Collection.class);
            Object index = indexMethod.invoke(linker, donors);
            Collection<IRule> candidates = (Collection<IRule>) method(
                    "selectDomainCandidates", List.class, Map.class).invoke(linker, receiver.getTree().get(0), index);
            List<Long> expectedDonors = new ArrayList<>();
            for (IRule donor : donors) if (donor.getId() != receiver.getId()) expectedDonors.add(donor.getId());
            List<Long> actualDonors = new ArrayList<>();
            for (IRule donor : candidates) actualDonors.add(donor.getId());
            require(actualDonors.equals(expectedDonors), "native donor membership and direction order");
            int[] before = depths(child);
            Method rotation = reference
                    ? method("rotator", Collection.class, Map.class, boolean.class)
                    : method("rotator", Collection.class, Collection.class, Map.class, boolean.class);
            Map<IRule, Set<Cause>> causes = new HashMap<>();
            boolean used = (Boolean) (reference
                    ? rotation.invoke(linker, execution, causes, false)
                    : rotation.invoke(linker, execution, donors, causes, false));
            require(java.util.Arrays.equals(before, depths(child)), "local factory checkpoints settle");
            require(linker.snapshotStatistics().getRuleVisits() == execution.size(), "only execution scope is visited");
            TVariable variable = domain.getArguments().getTVariables(child).iterator().next();
            boolean alpha = child.getTValues().find(variable, child.getTerms().add("alpha")) != null;
            boolean beta = child.getTValues().find(variable, child.getTerms().add("beta")) != null;
            require(alpha == expectBindings && beta == expectBindings, "receiver bindings respect donor universe");
            System.out.println("DONOR_SCOPE_CASE name=" + name + " order=" + (descending ? "descending" : "ascending")
                    + " execution=" + execution.size() + " donors=" + donors.size()
                    + " candidate_donors=" + actualDonors.size() + " alpha=" + alpha + " beta=" + beta + " used=" + used);
        } finally {
            base.release(child);
        }
        require(base.pendingTransactionCount() == 0, "child reservation released");
        require(Boolean.TRUE.equals(base.query("?seed(alpha);", null, false)), "base positive fact unchanged");
        require(Boolean.FALSE.equals(base.query("?~seed(alpha);", null, false)), "receiver does not escape to base");
        require(base.pendingTransactionCount() == 0, "base queries settle");
    }

    private static List<IRule> scope(String name, List<IRule> full, Rule receiver) {
        if ("full".equals(name)) return full;
        if ("receiver".equals(name)) return Collections.<IRule>singletonList(receiver);
        return Collections.emptyList();
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = Linker.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try { Field f = cursor.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static int depth(Object owner, String name) throws Exception {
        return ((Stack<?>) field(owner.getClass(), name).get(owner)).size();
    }

    private static int[] depths(Mind mind) throws Exception {
        Object t = mind.getTValues(), f = mind.getFValues();
        Object tc = field(t.getClass(), "cache").get(t), fc = field(f.getClass(), "cache").get(f);
        return new int[]{depth(tc, "stack"), depth(t, "additionsStack"), depth(t, "actionStack"),
                depth(fc, "stack"), depth(f, "invalidatedStack"), depth(f, "actionStack")};
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        ++checks;
    }
}
