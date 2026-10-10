/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

public final class DmzUnaryStateDeltaRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-unit-delta-").toString());
        for (String mode : new String[]{"direct", "recursive", "negative", "extra", "delete", "unsupported", "budget"}) run(mode);
        System.out.println("DMZ_UNARY_STATE_DELTA_PASS checks=" + checks);
    }
    private static void run(String mode) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            String rule = mode.equals("unsupported") ? "!@x (source(x) && other(x)) -> derived(x);"
                    : mode.equals("recursive") ? "!@x source(x) -> middle(x);" : "!@x source(x) -> derived(x);";
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 10, DmzReplayProvenance.Authority.EXTERNAL, rule)), "native production");
            if (mode.equals("recursive")) DmzReplayProvenance.acceptRule(q, source, 11, DmzReplayProvenance.Authority.EXTERNAL, "!@x middle(x) -> derived(x);");
            DmzUnaryStateDelta delta = DmzUnaryStateDelta.beforeInput(q, mode.equals("budget") ? 1 : 10000);
            String input = mode.equals("negative") ? "!~derived(1);" : "!source(1);";
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 20, DmzReplayProvenance.Authority.EXTERNAL, input)), "native input");
            long id = journal.snapshot().get(journal.snapshot().size()-1).nativeRule;
            DmzUnaryStateDelta.Result result = delta.audit(q, id);
            boolean supported = !mode.equals("unsupported") && !mode.equals("budget");
            require(result.matched == supported && !result.complete, "finite delta before mutation: " + result.gaps);
            require(delta.audit(q, id).matched == result.matched, "repeated audit stable");
            require(!delta.audit(new Mind(user), id).matched, "different target rejected");
            if (mode.equals("extra")) {
                q.compileLine("!unrelated(2);", false, new java.util.LinkedList<org.kanger.interfaces.ITerm>());
                require(!delta.audit(q, id).matched, "unrelated new primary rejected");
            }
            if (mode.equals("delete")) {
                // Fault injection: normal deletion can preserve/rederive a still-supported consequence.
                for (org.kanger.interfaces.IRule candidate : q.getRules())
                    if (q.getRules().isGenerated((org.kanger.units.Rule) candidate))
                        ((org.kanger.units.Rule) candidate).setDeleted(true, q);
                require(!delta.audit(q, id).matched, "missing expected consequence rejected");
            }
            if (mode.equals("budget")) require(result.truncated, "budget exhaustion explicit");
            if (mode.equals("unsupported")) require(!result.supported, "conjunction outside unary fragment");
        }
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
