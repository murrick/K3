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
        queryBoundary();
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
    private static void replay(Mind mind, IContextResults.Revision pin, long id, String text) throws Exception {
        require(Boolean.TRUE.equals(mind.query(text, null, false)), "native primary accepted");
        DmzReplayProvenance.replayRule(mind, pin, id, DmzReplayProvenance.Authority.EXTERNAL, text);
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
