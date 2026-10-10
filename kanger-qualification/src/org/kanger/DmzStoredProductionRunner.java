/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.IRule;
import org.kanger.udf.UDF;

/** Stored native results retain detached causes and full transaction settlement. */
public final class DmzStoredProductionRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-stored-production-").toString());
        run(false, true, false); run(true, true, false); run(true, false, false); run(false, true, true);
        queryBoundary(); alternatives(); siblingOperations();
        System.out.println("DMZ_STORED_PRODUCTION_PASS checks=" + checks);
    }
    private static void run(boolean recursive, boolean commit, boolean existing) throws Exception {
        String name = "dmz-stored-" + UUID.randomUUID();
        User user = new User(); user.setDatabaseDir(System.getProperty("user.home") + java.io.File.separator);
        new UDF().init(user); new org.kanger.storage.dumb2.DB().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        q = (Mind) q.useStorage(name); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision facts = new IContextResults.Revision(UUID.randomUUID(), 2);
        try {
            List<TerminalSupportCapture.StoredObservation> detached;
            try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
                replay(q, rules, 10, "!@x source(x) -> " + (recursive ? "middle" : "derived") + "(x);");
                if (recursive) replay(q, rules, 11, "!@x middle(x) -> derived(x);");
                if (existing) replay(q, facts, 21, "!derived(1);");
                try (TerminalSupportCapture capture = TerminalSupportCapture.begin(q);
                        TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) {
                    require(Boolean.TRUE.equals(outer.mind().query("!source(1);", null, false)), "native inference accepted");
                    List<TerminalSupportCapture.StoredObservation> pending = capture.storedSnapshot();
                    if (existing) {
                        require(pending.isEmpty(), "canonical primary hit suppresses stored-result write");
                        boolean preserved = false;
                        for (IRule rule : outer.mind().getRules())
                            for (DmzReplayProvenance.Binding source : journal.sources(outer.mind(), rule.getId()))
                                if (source.context.equals(facts.getContextId()) && source.sourceRule == 21) {
                                    preserved = true;
                                    require(!outer.mind().getRules().isGenerated(rule), "primary canonical identity retained");
                                }
                        require(preserved, "canonical primary provenance retained");
                        require(!capture.applicationSnapshot().isEmpty(), "suppressed result still has application observations");
                        require(outer.commit(), "canonical-hit outer commit");
                        require(capture.storedSnapshot().isEmpty(), "no fabricated materialization for suppressed write");
                        return;
                    }
                    require(!pending.isEmpty(), "stored results observed");
                    boolean derived = false;
                    for (TerminalSupportCapture.StoredObservation item : pending) {
                        require(item.outcome == TerminalSupportCapture.Outcome.PENDING, "inner commit awaits outer settlement");
                        CollisionProofCapture.Node root = item.materialization.causes.nodes.get(item.materialization.causes.root);
                        require(root.nativeRule == item.materialization.nativeRule && root.generated == item.materialization.generated,
                                "stored identity and generated status agree");
                        require(!item.routes.isEmpty(), "stored result joins observed applications");
                        boolean producer = false;
                        for (TerminalSupportCapture.Application route : item.routes) {
                            producer |= item.fromProducer(route);
                            require(route.mind == item.materialization.mind && root.ground.equivalent(route.ground),
                                    "same native inference Mind and typed ground conclusion");
                            require(!TerminalSupportCapture.matchesStored(item.materialization, route, -1)
                                    && !TerminalSupportCapture.matchesStored(item.materialization, route, item.materialization.operation + 1),
                                    "unknown or different operation cannot join");
                            TerminalSupportCapture.Application wrongMind = new TerminalSupportCapture.Application(
                                    route.mind + 100000, route.rule, route.ruleOrigin, route.conclusion, route.ground,
                                    route.bindings, route.supports);
                            require(!TerminalSupportCapture.matchesStored(item.materialization, wrongMind, item.materialization.operation),
                                    "same atom in sibling Mind cannot join");
                            TerminalSupportCapture.Ground opposite = new TerminalSupportCapture.Ground(
                                    route.ground.predicate, !route.ground.sign, route.ground.arguments);
                            TerminalSupportCapture.Application wrongSign = new TerminalSupportCapture.Application(
                                    route.mind, route.rule, route.ruleOrigin, route.conclusion, opposite, route.bindings, route.supports);
                            require(!TerminalSupportCapture.matchesStored(item.materialization, wrongSign, item.materialization.operation),
                                    "same rendering with opposite typed sign cannot join");
                        }
                        require(producer, "stored originating production has an observed application");
                        if (root.ground == null || !root.ground.predicate.equals("derived")) continue;
                        derived = true;
                        require(root.generated, "new stored consequence is generated");
                        require(root.sources.isEmpty(), "generated result receives no primary replay label");
                        boolean ruleSource = false, factSource = false;
                        for (CollisionProofCapture.Node node : item.materialization.causes.nodes)
                            for (DmzReplayProvenance.SourceObservation source : node.sources) {
                                ruleSource |= source.binding.context.equals(rules.getContextId());
                                factSource |= source.binding.context.equals(facts.getContextId());
                            }
                        require(ruleSource, "stored cause graph retains pinned production origin");
                        require(!factSource, "new native fact receives no invented replay attribution");
                        require(!root.causes.isEmpty(), "stored result has cause edges");
                    }
                    require(derived, "derived materialization observed");
                    if (commit) require(outer.commit(), "outer commit"); else outer.rollback();
                    detached = capture.storedSnapshot();
                    for (TerminalSupportCapture.StoredObservation item : detached)
                        require(item.outcome == (commit ? TerminalSupportCapture.Outcome.COMMITTED : TerminalSupportCapture.Outcome.ROLLED_BACK),
                                "outer settlement applies to all materializations");
                }
            }
            require(!detached.isEmpty() && !detached.get(0).materialization.causes.nodes.isEmpty(),
                    "detached stored graph survives capture and journal closure");
            int generated = 0;
            for (IRule rule : q.getRules()) if (q.getRules().isGenerated(rule)) ++generated;
            require(commit ? generated > 0 : generated == 0, "durable state follows outer settlement");
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void queryBoundary() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            DmzReplayProvenance.replayRule(q, pin, 10, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x source(x) -> derived(x);");
            DmzReplayProvenance.replayRule(q, pin, 11, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
            try (TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
                require(Boolean.TRUE.equals(q.query("?derived(1);", null, false)), "ephemeral query succeeds");
                require(!capture.storedSnapshot().isEmpty(), "query materialization observed");
                for (TerminalSupportCapture.StoredObservation item : capture.storedSnapshot())
                    require(item.outcome == TerminalSupportCapture.Outcome.ROLLED_BACK,
                            "query-local stored result is not a durable accepted write: " + item.outcome);
            }
        }
    }
    private static void alternatives() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            replay(q, pin, 10, "!@x source(x) -> derived(x);");
            replay(q, pin, 11, "!@x source(x) -> middle(x);");
            replay(q, pin, 12, "!@x middle(x) -> derived(x);");
            try (TerminalSupportCapture capture = TerminalSupportCapture.begin(q);
                    TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) {
                require(Boolean.TRUE.equals(outer.mind().query("!source(1);", null, false)), "alternative routes inference");
                boolean joined = false;
                for (TerminalSupportCapture.StoredObservation item : capture.storedSnapshot()) {
                    CollisionProofCapture.Node root = item.materialization.causes.nodes.get(item.materialization.causes.root);
                    if (root.ground == null || !root.ground.predicate.equals("derived")) continue;
                    java.util.Set<Integer> productions = new java.util.HashSet<Integer>();
                    boolean directProducer = false;
                    for (TerminalSupportCapture.Application route : item.routes) {
                        productions.add(route.rule); directProducer |= item.fromProducer(route);
                    }
                    require(productions.size() == 2 && directProducer,
                            "stored native identity retains direct and recursive routes, including later suppressed result");
                    joined = true;
                }
                require(joined, "alternative stored result present");
                require(outer.commit(), "alternative routes commit");
                for (TerminalSupportCapture.StoredObservation item : capture.storedSnapshot())
                    require(item.outcome == TerminalSupportCapture.Outcome.COMMITTED, "joined routes share stored settlement");
            }
        }
    }
    private static void siblingOperations() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            replay(q, pin, 10, "!@x source(x) -> derived(x);");
            try (TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
                List<TerminalSupportCapture.StoredObservation> before;
                try (TechnicalMindTransaction first = TechnicalMindTransaction.begin(q)) {
                    require(Boolean.TRUE.equals(first.mind().query("!source(1);", null, false)), "first sibling inference");
                    before = capture.storedSnapshot();
                    require(!before.isEmpty() && !before.get(0).routes.isEmpty(), "first sibling routes observed");
                    first.rollback();
                }
                try (TechnicalMindTransaction second = TechnicalMindTransaction.begin(q)) {
                    require(Boolean.TRUE.equals(second.mind().query("!source(1);", null, false)), "same atom in second sibling");
                    require(second.commit(), "second sibling commits");
                }
                boolean committed = false, discarded = false;
                for (TerminalSupportCapture.StoredObservation item : capture.storedSnapshot()) {
                    require(!item.routes.isEmpty(), "each sibling retains its own routes");
                    for (TerminalSupportCapture.Application route : item.routes)
                        require(route.mind == item.materialization.mind, "identical atom does not merge sibling routes");
                    if (item.outcome == TerminalSupportCapture.Outcome.COMMITTED) {
                        committed = true;
                        for (TerminalSupportCapture.Application route : before.get(0).routes)
                            require(!item.routes.contains(route), "rolled-back route cannot enter committed result");
                    } else if (item.outcome == TerminalSupportCapture.Outcome.ROLLED_BACK) discarded = true;
                }
                require(committed && discarded, "both sibling settlements retained independently");
                require(before.get(0).outcome == TerminalSupportCapture.Outcome.PENDING,
                        "previous detached snapshot is immutable historical observation");
            }
        }
    }
    private static void replay(Mind mind, IContextResults.Revision pin, long id, String text) throws Exception {
        require(Boolean.TRUE.equals(mind.query(text, null, false)), "native primary accepted");
        DmzReplayProvenance.replayRule(mind, pin, id, DmzReplayProvenance.Authority.EXTERNAL, text);
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
