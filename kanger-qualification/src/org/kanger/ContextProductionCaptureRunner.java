/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.ITerm;
import org.kanger.ContextProductionCapture.*;

/** Native production journal qualification; does not certify a round executor. */
public final class ContextProductionCaptureRunner {
    private static int checks;
    private static final UUID X = UUID.randomUUID(), A = UUID.randomUUID(), B = UUID.randomUUID();
    public static void main(String[] args) {
        try {
            System.setProperty("user.home", java.nio.file.Files.createTempDirectory("production-capture-").toString());
            joinedApplication();
            maskedConclusionAndAlternatives();
            negativeProduct();
            sourceAliases();
            multipleCompiledBranches();
            quantifiedClauseTransport();
            eigenparameterFreshness();
            typedDetachedTransport();
            nullaryAndRepeatedArguments();
            queryArtifactsAreExcluded();
            unsupportedInputsAreVisible();
            boundedScopeAndInactiveHooks();
            System.out.println("CONTEXT_PRODUCTION_CAPTURE_PASS checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static Mind memory() throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind); return mind;
    }
    private static Source source(UUID context, long rule) { return new Source(context, 1, rule, context.equals(X), "base"); }
    private static Snapshot replay(String[] statements, Source[] sources) throws Exception {
        Mind work = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(work, 10000)) {
            for (int i = 0; i < statements.length; ++i) capture.replay(work, sources[i], statements[i]);
            work.setQueryPass(QueryPass.CHECK); work.link(null, false);
            require(!work.analyze(null, false), "fixture locally consistent");
            Snapshot snapshot = capture.snapshot();
            require(snapshot.gaps.isEmpty(), "ordinary applications have no projection gaps: " + snapshot.gaps);
            return snapshot;
        }
    }
    private static void joinedApplication() throws Exception {
        Snapshot snapshot = replay(new String[] {"!@x left(x) && right(x) -> p(x);", "!left(1);", "!right(1);"},
                new Source[] {source(X, 0), source(A, 0), source(B, 0)});
        Application result = application(snapshot, "p", true, X, 0);
        require(result != null, "joined production captured");
        require(result.premises.size() == 2, "whole two-premise donor group retained");
        require(hasPremise(result, "left", true) && hasPremise(result, "right", true), "donor polarity retained");
        require(result.producers.get(0).localX, "producer is labelled X");
    }
    private static void maskedConclusionAndAlternatives() throws Exception {
        Snapshot snapshot = replay(new String[] {"!p(1);", "!@x a(x) -> p(x);", "!@x c(x) -> p(x);", "!a(1);", "!c(1);"},
                new Source[] {source(A, 0), source(X, 0), source(X, 1), source(A, 1), source(B, 0)});
        Application first = application(snapshot, "p", true, X, 0), second = application(snapshot, "p", true, X, 1);
        require(first != null && second != null, "both routes retained beside an existing foreign p");
        require(first.conclusion.equivalent(second.conclusion), "alternative routes reach a semantic twin");
        require(hasPremise(first, "a", true) && hasPremise(second, "c", true), "independent donor groups preserved");
        require(snapshot.inputs.get(0).source.context.equals(A) && snapshot.inputs.get(0).fact != null,
                "foreign primary p remains a distinct support");
        boolean immutable = false;
        try { snapshot.applications.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "detached applications immutable");
    }
    private static void negativeProduct() throws Exception {
        Snapshot snapshot = replay(new String[] {"!@x left(x) && right(x) -> p(x);", "!right(1);", "!~p(1);"},
                new Source[] {source(X, 0), source(B, 0), source(B, 1)});
        Application negative = application(snapshot, "left", false, X, 0);
        require(negative != null, "actual counterexample negative product captured");
        require(hasPremise(negative, "right", true) && hasPremise(negative, "p", false),
                "negative product has both concrete donor supports");
        Mind target = memory();
        require(target.compileLine(negative.conclusion.assertion(), false, negative.conclusion.project(target)) != null,
                "detached negative product canonically replayed");
        require(Boolean.TRUE.equals(target.query("?~left(1);", null, false)), "replay preserves negative sign");
    }
    private static void sourceAliases() throws Exception {
        Snapshot snapshot = replay(new String[] {"!a(1);", "!a(1);", "!@x a(x) -> p(x);"},
                new Source[] {source(A, 0), source(B, 0), source(X, 0)});
        Input a = snapshot.inputs.get(0), b = snapshot.inputs.get(1);
        require(a.fact.equivalent(b.fact) && a.nativeRule == b.nativeRule, "semantic duplicate uses one native rule");
        require(!a.source.equals(b.source) && b.duplicate, "both exact source aliases retained");
        require(application(snapshot, "p", true, X, 0) != null, "duplicate donor remains available to production");
        snapshot = replay(new String[] {"!@x a(x) -> p(x);", "!@x a(x) -> p(x);", "!a(1);"},
                new Source[] {source(X, 0), source(A, 0), source(B, 0)});
        Application result = application(snapshot, "p", true, X, 0);
        require(result != null && result.producers.size() == 2, "same rule has local and foreign source alternatives");
    }
    private static void multipleCompiledBranches() throws Exception {
        Snapshot snapshot = replay(new String[] {"!(p(1) || q(1)) && (r(2) || s(2));", "!~q(1);", "!~s(2);"},
                new Source[] {source(X, 0), source(A, 0), source(B, 0)});
        Input primary = snapshot.inputs.get(0);
        require(primary.clauses.size() == 2 && primary.clause == null,
                "all ground clauses of one compiled source are detached separately");
        for (Clause clause : primary.clauses)
            require(clause.literals.size() == 2, "compiled disjunctions remain intact");
        Application p = application(snapshot, "p", true, X, 0), r = application(snapshot, "r", true, X, 0);
        require(p != null && r != null && p.nativeRule == r.nativeRule,
                "native applications from different branches retain the same original source occurrence");
        require(hasPremise(p, "q", false) && hasPremise(r, "s", false),
                "branches retain their own complete donor groups");
        boolean immutable = false;
        try { primary.clauses.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "multi-clause input collection is detached and immutable");
        snapshot = replay(new String[] {"!@x seed(x) -> (mid(x) && tail(x));",
                "!@x seed(x) -> (mid(x) && tail(x));", "!seed(John);"},
                new Source[] {source(X, 1), source(A, 1), source(B, 1)});
        for (String predicate : new String[] {"mid", "tail"}) {
            Application result = application(snapshot, predicate, true, X, 1);
            require(result != null && result.producerSupports.size() == 2,
                    "every multi-head branch retains both semantic-duplicate source alternatives");
            for (Support support : result.producerSupports)
                require(support.sources.size() == 1 && support.depth == 0,
                        "primary aliases remain alternative singleton supports");
        }
        Mind work = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(work, 10000)) {
            capture.replay(work, source(X, 2), "!$x known(1) && abstract(x);");
            Snapshot incomplete = capture.snapshot();
            require(incomplete.inputs.get(0).clauses.size() == 1 && !incomplete.gaps.isEmpty(),
                    "a concrete branch cannot hide an unsupported abstract unit sibling: clauses="
                            + incomplete.inputs.get(0).clauses.size() + " gaps=" + incomplete.gaps);
        }
    }
    private static Clause template(String statement) throws Exception {
        Mind work = memory();
        org.kanger.units.Rule rule = (org.kanger.units.Rule) work.compileLine(statement, false, new LinkedList<ITerm>());
        return Clause.of(rule.getTree().get(0), work, true);
    }
    private static void eigenparameterFreshness() throws Exception {
        Mind work = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(work,10000)) {
            capture.replay(work,source(X,0),"!p(1);");
            SemanticTermSnapshot known = capture.snapshot().inputs.get(0).fact.arguments.get(0);
            boolean refused = false;
            try { capture.verifyParametersFresh(Collections.singletonList(known)); }
            catch (IllegalStateException expected) { refused = true; }
            require(refused,"an existing constant cannot be generalized as an arbitrary fresh parameter");
            SemanticTermSnapshot fresh = SemanticTermSnapshot.capture(work.getTerms().add("ctxfresh_"+UUID.randomUUID().toString().replace("-","")));
            capture.verifyParametersFresh(Collections.singletonList(fresh));
            require(capture.snapshot().inputs.size() == 1,
                    "fresh parameter verification installs no assertion or knowledge source");
        }
    }
    private static void quantifiedClauseTransport() throws Exception {
        Snapshot snapshot = replay(new String[] {"!@x @y left(x) && right(y) -> p(x,y);", "!left(John);"},
                new Source[] {source(X, 0), source(A, 0)});
        Residual residual = null;
        for (Residual event : snapshot.residuals) if (event.conclusion.variables.size() == 1) { residual = event; break; }
        require(residual != null && residual.premises.size() == 1, "partially bound native residual retains its ordinary excluded premise");
        Clause clause = residual.conclusion;
        require(clause.literals.size() == 2 && clause.variables.size() == 1,
                "remaining variable is scoped once across the whole disjunction");
        for (Atom atom : clause.literals) require(atom.fact() == null, "variable-containing atoms never masquerade as ground facts");
        Mind target = memory();
        target.compileLine(clause.assertion(), false, clause.project(target));
        require(target.query("?p(John,2);", null, false) == null, "a quantified residual does not assert its head");
        require(target.compile("!right(2);", null, false), "fresh participant supplies the bound donor");
        require(Boolean.TRUE.equals(target.query("?p(John,2);", null, false)), "typed constant and remaining universal variable survive native replay");
        require(target.query("?p(2,John);", null, false) == null, "quantified transport preserves argument order");
        Snapshot hygienic = replay(new String[] {"!@x @y left(x) && right(y) -> p(x,y);", "!left(ctxroundv0);"},
                new Source[] {source(X, 3), source(A, 3)});
        Clause sameName = null;
        for (Residual event : hygienic.residuals) if (!event.conclusion.variables.isEmpty()) { sameName = event.conclusion; break; }
        Mind names = memory(); names.compileLine(sameName.assertion(), false, sameName.project(names));
        names.compile("!right(2);", null, false);
        require(Boolean.TRUE.equals(names.query("?p(ctxroundv0,2);", null, false)),
                "a named constant equal to the carrier binder name remains a constant");
        require(names.query("?p(John,2);", null, false) == null,
                "placeholder projection prevents accidental variable capture of a named constant");
        Clause original = template("!@x @y (p(x,y) || q(y,x));");
        Clause renamed = template("!@a @b (q(b,a) || p(a,b));");
        require(original.equivalent(renamed) && renamed.equivalent(original),
                "alpha renaming and literal order preserve the same scoped clause");
        Clause different = template("!@x @y (p(x,y) || q(x,y));");
        require(!original.equivalent(different), "alpha comparison preserves cross-literal variable relationships");
        Clause independent = template("!@x @y (p(x) || p(y));");
        Clause repeated = template("!@x (p(x) || p(x));");
        require(independent.literals.size() == 2 && independent.variables.size() == 2
                && repeated.literals.size() == 1 && repeated.variables.size() == 1,
                "deduplication cannot merge independently quantified slots");
        Clause repeatedArgument = template("!@x (p(x,x) || q(x));");
        Clause distinctArguments = template("!@x @y (p(x,y) || q(y));");
        require(!repeatedArgument.equivalent(distinctArguments), "repeated argument identity is part of clause semantics");
        Mind replay = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(replay, 10000)) {
            capture.replayDerived(replay, clause, Collections.singletonList(
                    new Support(new LinkedHashSet<>(Arrays.asList(source(X,0),source(A,0))), 1)));
            Input input = capture.snapshot().inputs.get(0);
            require(input.source == null && input.clause.equivalent(clause) && input.fact == null
                    && capture.snapshot().gaps.isEmpty(), "derived template binds native provenance without inventing a primary fact");
        }
    }
    private static void typedDetachedTransport() throws Exception {
        Snapshot snapshot = replay(new String[] {"!@x @y pair(x,y) -> reverse(y,x);", "!pair(John,2);"},
                new Source[] {source(X, 0), source(A, 0)});
        Application result = application(snapshot, "reverse", true, X, 0);
        require(result != null && result.conclusion.arguments.size() == 2, "binary typed product captured");
        Mind target = memory(); target.compileLine(result.conclusion.assertion(), false, result.conclusion.project(target));
        require(Boolean.TRUE.equals(target.query("?reverse(2,John);", null, false)), "positional canonical transport");
        require(target.query("?reverse(John,2);", null, false) == null, "arguments are not interchanged");
    }
    private static void queryArtifactsAreExcluded() throws Exception {
        Mind work = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(work, 10000)) {
            capture.replay(work, source(A, 0), "!@x a(x) && b(x) -> r(x);");
            capture.replay(work, source(A, 1), "!~r(1);");
            require(work.query("?b(1);", null, false) == null, "conditional-query fixture");
            require(!work.getHypothesis().isEmpty(), "query retains its local hypothesis");
            require(capture.snapshot().applications.isEmpty(), "conditional query products do not enter qualification journal");
        }
    }
    private static void nullaryAndRepeatedArguments() throws Exception {
        Snapshot snapshot = replay(new String[] {"!@x a(x) -> ready();", "!a(1);"},
                new Source[] {source(X, 0), source(A, 0)});
        Application result = application(snapshot, "ready", true, X, 0);
        require(result != null && result.conclusion.arguments.isEmpty(), "nullary result is detached");
        Mind target = memory(); target.compileLine(result.conclusion.assertion(), false, result.conclusion.project(target));
        require(Boolean.TRUE.equals(target.query("?ready();", null, false)), "nullary result canonically transported");
        snapshot = replay(new String[] {"!@x pair(x,x) -> same(x);", "!pair(1,2);"},
                new Source[] {source(X, 0), source(A, 0)});
        require(application(snapshot, "same", true, X, 0) == null, "repeated variable cannot accept mismatched arguments");
        snapshot = replay(new String[] {"!@x pair(x,x) -> same(x);", "!pair(1,1);"},
                new Source[] {source(X, 0), source(A, 0)});
        require(application(snapshot, "same", true, X, 0) != null, "equal repeated arguments preserve native proof");
    }
    private static void boundedScopeAndInactiveHooks() throws Exception {
        Mind work = memory();
        try (ContextProductionCapture outer = ContextProductionCapture.begin(work, 10000)) {
            outer.replay(work, source(A, 0), "!a(1);");
            Mind innerMind = memory();
            try (ContextProductionCapture inner = ContextProductionCapture.begin(innerMind, 1)) {
                inner.replay(innerMind, source(B, 0), "!b(1);");
                boolean stopped = false;
                try { inner.replay(innerMind, source(B, 1), "!c(1);"); }
                catch (IllegalStateException expected) { stopped = expected.getMessage().contains("incomplete"); }
                require(stopped, "budget stop is explicit incomplete qualification");
                require(inner.snapshot().gaps.contains("observation-budget-exhausted"), "partial journal remains marked incomplete");
            }
            outer.replay(work, source(A, 1), "!d(1);");
            require(outer.snapshot().inputs.size() == 2, "nested scope restores outer journal");
            Mind unrelated = memory(); unrelated.compile("!@x a(x) -> b(x); !a(1);", null, false);
            require(outer.snapshot().applications.isEmpty(), "unrelated Mind cannot contaminate journal");
        }
        require(work.compile("!@x a(x) -> p(x);", null, false), "inactive hooks preserve normal compile");
        require(Boolean.TRUE.equals(work.query("?p(1);", null, false)), "inactive hooks preserve normal proof");
    }
    private static void unsupportedInputsAreVisible() throws Exception {
        Mind work = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(work, 10000)) {
            capture.replay(work, source(X, 0), "!$x p(x);");
            require(!capture.snapshot().gaps.isEmpty(), "abstract unit input cannot masquerade as complete ground capture");
        }
        Mind witness = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(witness, 10000)) {
            capture.replay(witness, source(X, 4), "!$x (p(x) || q(x));");
            require(capture.snapshot().gaps.toString().contains("cvariable-input:"),
                    "an unresolved existential disjunction cannot silently pass the supported-source gate");
        }
        work = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(work, 10000)) {
            boolean refused = false;
            try { capture.replay(work, source(X, 0), "!a(1); !b(1);"); }
            catch (IllegalArgumentException expected) { refused = true; }
            require(refused && capture.snapshot().inputs.isEmpty(), "one source label cannot silently bind a multi-command payload");
        }
    }
    private static boolean hasPremise(Application a, String predicate, boolean positive) {
        for (Fact premise : a.premises) if (predicate.equals(premise.predicate) && positive == premise.positive) return true;
        return false;
    }
    private static Application application(Snapshot s, String predicate, boolean positive, UUID context, long rule) {
        for (Application a : s.applications) if (predicate.equals(a.conclusion.predicate) && positive == a.conclusion.positive)
            for (Source producer : a.producers) if (producer.context.equals(context) && producer.rule == rule) return a;
        return null;
    }
    private static void require(boolean value, String message) {
        ++checks; if (!value) throw new AssertionError(message);
    }
}
