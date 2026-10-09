/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Replay ancestry acceptance, distinct from persistent revision certification. */
public final class DmzReplaySettlementRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-replay-settle-").toString());
        nested(true); nested(false); late(); automatic(); explicit();
        System.out.println("DMZ_REPLAY_SETTLEMENT_PASS checks=" + checks);
    }
    private static Mind root() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind mind = new Mind(user); user.setCurrentMind(mind); return mind;
    }
    private static void replay(Mind target) throws Exception {
        DmzReplayProvenance.replayRule(target, new IContextResults.Revision(UUID.randomUUID(), 1),
                1, DmzReplayProvenance.Authority.EXTERNAL, "!p(John);");
    }
    private static void nested(boolean commit) throws Exception {
        Mind q = root();
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) {
            replay(outer.mind()); replay(outer.mind());
            DmzReplayProvenance.Settlement pending = journal.settlementSnapshot();
            require(pending.pending == 2 && pending.accepted.isEmpty(), "new and duplicate wait for ancestor");
            if (commit) require(outer.commit(), "outer commit"); else outer.rollback();
            DmzReplayProvenance.Settlement settled = journal.settlementSnapshot();
            require(settled.pending == 0 && settled.untracked == 0, "known lineage settled");
            require(commit ? settled.accepted.size() == 2 : settled.discarded == 2, "ancestor controls bindings");
            require(pending.pending == 2, "detached earlier snapshot");
            require(journal.snapshot().size() == 2, "raw diagnostics retained");
        }
    }
    private static void late() throws Exception {
        try (TechnicalMindTransaction outer = TechnicalMindTransaction.begin(root());
                DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            replay(outer.mind());
            require(journal.settlementSnapshot().untracked == 1, "unknown ancestor fails closed");
            outer.rollback();
        }
    }
    private static void automatic() throws Exception {
        Mind q = root();
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            try (TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) { replay(outer.mind()); }
            require(journal.settlementSnapshot().discarded == 1, "automatic rollback discards");
        }
    }
    private static void explicit() throws Exception {
        try (TechnicalMindTransaction branch = TechnicalMindTransaction.beginIsolated(root());
                DmzReplayProvenance journal = DmzReplayProvenance.begin(branch.mind())) {
            replay(branch.mind());
            require(journal.settlementSnapshot().accepted.size() == 1, "explicit local boundary");
            try { journal.settlementSnapshot().accepted.clear(); throw new AssertionError("mutable"); }
            catch (UnsupportedOperationException expected) { ++checks; }
            branch.rollback();
        }
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
