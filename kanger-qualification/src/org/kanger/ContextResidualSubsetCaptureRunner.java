/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.enums.QueryPass;

/** Actual exclusion observations, with no waiter assumptions or native writes. */
public final class ContextResidualSubsetCaptureRunner {
    private static int checks;
    private static Source source(int id) { return new Source(new UUID(0,id),1,0,id == 0,"base"); }
    private static Mind memory() throws Exception { User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); return m; }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Snapshot capture(boolean subsets,String... facts) throws Exception {
        Mind mind = memory(); mind.setQueryPass(QueryPass.ACCEPT);
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,10000)) {
            if (subsets) capture.observeResidualSubsets();
            capture.replay(mind,source(0),"!a(1) && b(1) -> r(1);");
            for (int i=0;i<facts.length;++i) capture.replay(mind,source(i+1),facts[i]);
            mind.link(null,false);
            return capture.snapshot();
        }
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("residual-subsets-").toString());
            Snapshot baseline = capture(false,"!a(1);","!b(1);");
            require(baseline.residuals.stream().noneMatch(r -> r.conclusion.literals.size() == 2),
                    "default capture retains historical observation scope");
            Snapshot enabled = capture(true,"!a(1);","!b(1);");
            require(enabled.gaps.isEmpty(),"ordinary exclusions have no capture gaps");
            for (String predicate : new String[]{"a","b"}) {
                require(enabled.residuals.stream().anyMatch(r -> r.conclusion.literals.size() == 2 && r.premises.size() == 1
                        && r.premises.get(0).predicate.equals(predicate) && r.premises.get(0).positive
                        && r.producerSupports.stream().anyMatch(p -> p.sources.equals(Collections.singleton(source(0))) && p.depth == 0)),
                        "observed exclusion has an independent residual obligation with exact producer label");
            }
            require(enabled.residuals.stream().anyMatch(r -> r.conclusion.literals.size() == 1 && r.premises.size() == 2),
                    "joint reduction also remains observed");
            require(capture(true).residuals.isEmpty(),"waiter-only hypotheses cannot supply excluded premises");
            Snapshot signed = capture(true,"!a(1);","!~r(1);");
            require(signed.residuals.stream().anyMatch(r -> r.conclusion.literals.size() == 2 && r.premises.size() == 1
                    && r.premises.get(0).predicate.equals("r") && !r.premises.get(0).positive),
                    "opposite head is an observed signed premise");
            Snapshot duplicate = capture(true,"!a(1);","!a(1);","!b(1);");
            require(duplicate.inputs.stream().filter(i -> i.source != null).count() == 4,"source aliases remain separate inputs");
            Mind mind = memory(); boolean stopped = false;
            try (ContextProductionCapture limited = ContextProductionCapture.begin(mind,4)) {
                limited.observeResidualSubsets(); mind.setQueryPass(QueryPass.ACCEPT);
                limited.replay(mind,source(0),"!a(1) && b(1) -> r(1);");
                limited.replay(mind,source(1),"!a(1);"); limited.replay(mind,source(2),"!b(1);");
                mind.link(null,false);
            } catch (IllegalStateException expected) { stopped = true; }
            require(stopped,"subset observations consume the existing event budget");
            System.out.println("CONTEXT_RESIDUAL_SUBSET_CAPTURE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
