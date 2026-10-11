/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Native stored identity -> recursive, source-labelled historical witnesses. */
public final class DmzStoredProofRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-stored-proof-").toString());
        run("full"); run("unlabelled"); run("rollback"); run("mixed-pins"); run("incompatible");
        System.out.println("DMZ_STORED_PROOF_PASS checks=" + checks);
    }
    private static void run(String mode) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision a = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision c = new IContextResults.Revision(mode.equals("incompatible") ? a.getContextId() : UUID.randomUUID(), 1);
        IContextResults.Revision fact = (mode.equals("mixed-pins") || mode.equals("incompatible"))
                ? new IContextResults.Revision(a.getContextId(), 2)
                : new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision duplicate = new IContextResults.Revision(UUID.randomUUID(), 1);
        DmzStoredProof detached;
        TerminalSupportCapture closedCapture;
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            rule(q, a, 10, "!@x source(x) -> derived(x);");
            rule(q, c, 11, "!@x source(x) -> middle(x);");
            rule(q, c, 12, "!@x middle(x) -> derived(x);");
            try (TerminalSupportCapture capture = TerminalSupportCapture.begin(q);
                    TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) {
                closedCapture = capture;
                require(Boolean.TRUE.equals(outer.mind().query("!source(1);", null, false)), "native accepted fact");
                TerminalSupportCapture.Materialization stored = null;
                for (TerminalSupportCapture.StoredObservation item : capture.storedSnapshot()) {
                    TerminalSupportCapture.Ground ground = item.materialization.causes.nodes.get(item.materialization.causes.root).ground;
                    if (ground != null && ground.predicate.equals("derived")) stored = item.materialization;
                }
                require(stored != null, "stored native derived identity");
                require(DmzStoredProof.build(capture, journal, stored, 1000).witnesses.isEmpty(),
                        "pending outer transaction supplies no accepted witnesses");
                if (!mode.equals("unlabelled")) {
                    // Explicit canonical replay labels are attached before outer settlement.
                    // This is a historical association, not an event-time baseline certificate.
                    DmzReplayProvenance.replayRule(outer.mind(), fact, 20, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
                    if (mode.equals("full")) DmzReplayProvenance.replayRule(outer.mind(), duplicate, 21,
                            DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
                }
                if (mode.equals("rollback")) outer.rollback(); else require(outer.commit(), "outer settlement");
                detached = DmzStoredProof.build(capture, journal, stored, 1000);
                require(!detached.complete && !detached.truncated, "historical witnesses never certify completeness");
                if (mode.equals("rollback")) {
                    require(detached.witnesses.isEmpty() && has(detached, "stored-not-committed:"), "rolled-back result has no accepted proof");
                } else if (mode.equals("unlabelled") || mode.equals("incompatible")) {
                    require(detached.root >= 0 && detached.witnesses.isEmpty()
                            && has(detached, "missing-compatible-source-witness"), "unlabelled or incompatible primary cannot produce a source witness");
                } else {
                    require(detached.witnesses.size() == (mode.equals("full") ? 4 : 1) && detached.gaps.isEmpty(),
                            "direct/recursive paths and primary alternatives remain distinct: " + detached.witnesses.size());
                    int direct = 0, recursive = 0;
                    boolean firstFact = false, secondFact = false;
                    for (DmzProofWitnesses.Witness witness : detached.witnesses) {
                        require(witness.graph == detached.graph && witness.node == detached.root, "witness targets stored graph root");
                        require(DmzProofWitnesses.compatible(witness, new java.util.HashMap<UUID, Long>()), "whole chain has compatible pins");
                        if (witness.source.context.equals(a.getContextId())) ++direct; else ++recursive;
                        DmzProofWitnesses.Witness leaf = witness;
                        while (!leaf.premises.isEmpty()) {
                            require(leaf.premises.size() == 1, "fixture retains unary chain"); leaf = leaf.premises.get(0);
                        }
                        require(leaf.step == -1 && leaf.source.sourceRule >= 20, "chain terminates at actual labelled primary occurrence");
                        firstFact |= leaf.source.context.equals(fact.getContextId());
                        secondFact |= leaf.source.context.equals(duplicate.getContextId());
                    }
                    require(firstFact && (mode.equals("full") ? secondFact && direct == 2 && recursive == 2 : direct == 0 && recursive == 1),
                            "conflicting source revision removes only incompatible direct witness");
                    require(DmzStoredProof.build(capture, journal, stored, 1).truncated, "small witness budget explicit");
                    try (DmzReplayProvenance otherJournal = DmzReplayProvenance.begin()) {
                        boolean rejected = false;
                        try { DmzStoredProof.build(capture, journal, stored, 1000); }
                        catch (IllegalStateException expected) { rejected = true; }
                        require(rejected, "inactive owning journal cannot resolve stored sources");
                    }
                }
                try (TerminalSupportCapture other = TerminalSupportCapture.begin(q)) {
                    boolean rejected = false;
                    try { DmzStoredProof.build(other, journal, stored, 1000); }
                    catch (IllegalArgumentException expected) { rejected = true; }
                    require(rejected, "foreign materialization rejected");
                }
            }
        }
        require(detached.stored.nativeRule >= 0 && detached.graph != null, "detached proof survives closure");
        boolean rejected = false;
        try { closedCapture.storedOutcome(detached.stored); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "closed capture cannot resolve new proof inputs");
    }
    private static void rule(Mind q, IContextResults.Revision pin, long id, String text) throws Exception {
        require(Boolean.TRUE.equals(q.query(text, null, false)), "native primary rule");
        DmzReplayProvenance.replayRule(q, pin, id, DmzReplayProvenance.Authority.EXTERNAL, text);
    }
    private static boolean has(DmzStoredProof proof, String prefix) {
        for (String gap : proof.gaps) if (gap.startsWith(prefix)) return true;
        return false;
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
