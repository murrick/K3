/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Concrete historical-proof work exhaustion after completed rounds must not leak a stable prefix. */
public final class ContextUniversalGroupedRoundsBudgetRunner {
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("grouped-rounds-budget-").toString());
        Snapshot x=ContextUniversalGroupedRoundsRunner.bind(true,"!anchor(1); !@u p(u);"),a=ContextUniversalGroupedRoundsRunner.bind(false,"!@u (p(u)&&b(u)->r(u))&&(p(u)&&r(u)->s(u));"),b=ContextUniversalGroupedRoundsRunner.bind(false,"!b(1);");
        Packet p=ContextPrivateWitnessPoolRunner.common(x);List<Snapshot> cuts=Arrays.asList(a,b);
        ContextUniversalCompoundExchange.Exchange input=ContextUniversalGroupedRoundsRunner.exchange(x,cuts,p,Collections.emptyMap());
        ContextUniversalGroupedRounds.Result r=ContextUniversalGroupedRoundsRunner.run(x,cuts,p,Collections.emptyMap(),input,16,8);
        ContextUniversalGroupedRoundsRunner.incomplete(r,"certificate-binding",2);
        ContextUniversalGroupedRoundsRunner.require(r.reason.contains("Ground transport work limit"),"actual declared work exhaustion, not semantic refutation");
        ContextUniversalGroupedChainAudit.Inspection audit=ContextUniversalGroupedChainAudit.inspect(x,cuts,p,Collections.emptyMap(),ContextUniversalGroupedRoundsRunner.units(input),input,ContextUniversalGroupedRoundsRunner.limits(16),ContextUniversalGroupedRoundsRunner.local(),8,r,ContextUniversalGroupedRoundsRunner.WORK);
        ContextUniversalGroupedRoundsRunner.require(!audit.audited&&audit.output==null,"actual budget refusal cannot become a stable-chain certificate");
        System.out.println("CONTEXT_UNIVERSAL_GROUPED_ROUNDS_BUDGET_PASS checks="+ContextUniversalGroupedRoundsRunner.checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
