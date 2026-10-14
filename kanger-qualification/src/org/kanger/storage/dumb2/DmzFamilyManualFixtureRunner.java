/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.kanger.*;
import org.kanger.interfaces.*;

/** Disposable manual family report over the public SDK; never an alternate reasoning engine. */
public final class DmzFamilyManualFixtureRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            if(args.length==0) {
                Path root=Files.createTempDirectory("dmz-family-manual-test-");
                String full=run(root.resolve("full"),64,128);
                require(full.contains("Qualified traversal: exhausted") && full.contains("Completed branches: 4"),"full report includes four qualified leaves");
                require(full.contains("Native answer: TRUE") && full.contains("Native answer: FALSE") && full.contains("Answer proofs:")
                        && full.contains("Selected restrictions:") && full.contains("Source rule:"),"report exposes both opinions and exact proof sources");
                String partial=run(root.resolve("partial"),3,128);
                require(partial.contains("Qualified traversal: partial") && partial.contains("Completed branches: 1")
                        && partial.contains("Invocation limit") && partial.contains("First unexplored choice:"),"partial report retains leaf and remaining choice suffix");
                String blocked=run(root.resolve("blocked"),64,1);
                require(blocked.contains("Choice limit") && blocked.contains("Completed branches: 0") && blocked.contains("Qualified traversal: partial"),"choice limit does not masquerade as exhaustion");
                System.out.println("DMZ_FAMILY_MANUAL_FIXTURE_PASS checks="+checks);
            } else {
                if(args.length!=3)throw new IllegalArgumentException("Output directory, invocation cap and choice cap required");
                String report=run(Paths.get(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]));
                System.out.print(report);
                System.out.println("Report saved: "+Paths.get(args[0]).toAbsolutePath().resolve("family-report.txt"));
            }
            System.exit(0);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static String run(Path root,int invocations,int choices) throws Exception {
        if(invocations<1 || invocations>10000 || choices<1 || choices>128)throw new IllegalArgumentException("Bounded invocation and choice limits required");
        Files.createDirectories(root);System.setProperty("user.home",root.toString());
        Path dbDir=root.resolve("databases");Files.createDirectories(dbDir);
        create(dbDir,"Q","!a(1); !a(2); !a(3); !b(4);");
        IContextResults.Revision a=create(dbDir,"A","!@x a(x) -> p(x);");
        IContextResults.Revision b=create(dbDir,"B","!b(1); !b(2); !@x b(x) -> ~p(x);");
        long trigger;
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(dbDir.resolve("A"),new RevisionRef(a.getContextId(),a.getRevision()),"manual-family-trigger")) {
            trigger=DmzSourceShape.inspect(snapshot.getMind(),100).declarations.get(0).sourceRule;
        }
        User user=new User();user.setDatabaseDir(dbDir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        String report;
        try {
            IContextResults.Revision pin=new IContextResults.Revision(db.getContextId(),db.getRevision());
            DmzSourcePlan.Request sources=new DmzSourcePlan.Request(pin,Arrays.asList(new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b)),1000);
            DmzSourcesQuery.Limits limits=new DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,128,1000000,choices);
            DmzSourcesQuery.Request seed=new DmzSourcesQuery.Request(sources,DmzSourcesQuery.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID,"?p(1);",limits,
                    new DmzSourcesQuery.RestrictionRequest(a,trigger,Collections.<String>emptyList(),10000,DmzSourcesQuery.RestrictionChoiceScope.OBSERVED_CAUSAL_CHAINS));
            String before=DmzSourceMaterializationQualification.fingerprint(q);
            DmzRestrictionFamily.Result family=DmzRestrictionFamily.explore(q,seed,invocations);
            require(before.equals(DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q,"manual family restores physical Q and owner");
            report=format(seed,family,invocations);
        }finally {user.setCurrentMind(q.closeStorage());}
        Files.write(root.resolve("family-report.txt"),report.getBytes(StandardCharsets.UTF_8));
        require(new String(Files.readAllBytes(root.resolve("family-report.txt")),StandardCharsets.UTF_8).equals(report),"saved report matches inspected historical output");
        return report;
    }
    private static String format(DmzSourcesQuery.Request seed,DmzRestrictionFamily.Result family,int invocations) {
        StringBuilder out=new StringBuilder("DMZ MANUAL FAMILY REPORT\n\n");
        out.append("Query: ").append(seed.queryStatement).append("\nQ: ").append(pin(seed.sources.expectedQ)).append('\n');
        for(DmzSourcePlan.Selection source:seed.sources.external)out.append("Source: ").append(source.locator).append(" @ ").append(pin(source.revision)).append('\n');
        out.append("Trigger: ").append(pin(seed.restrictions.triggerRevision)).append(':').append(seed.restrictions.triggerRule)
                .append("\nInvocation cap: ").append(invocations).append("\nChoice cap: ").append(seed.limits.maximumVisits)
                .append("\nQualified traversal: ").append(family.exhausted?"exhausted":"partial")
                .append("\nEvaluated prefixes: ").append(family.observations.size()).append("\nCompleted branches: ").append(family.leaves.size())
                .append("\nUncovered branches: ").append(family.uncovered.size()).append("\nPending sibling groups: ").append(family.pending.size())
                .append("\nPhysical Q: unchanged (verified)\n\n");
        int number=0;
        for(DmzSourcesQuery.Result leaf:family.leaves) {
            out.append("BRANCH ").append(++number).append(" — completed\n");
            prefix(out,leaf);
            for(DmzSourcesQuery.Opinion opinion:leaf.opinions) {
                out.append("Native answer: ").append(opinion.answer.value==null?"UNKNOWN":opinion.answer.value?"TRUE":"FALSE").append("\nAnswer proofs:\n");
                for(DmzSourcesQuery.Proof proof:opinion.answer.proofs)proof(out,proof,"  ");
                if(opinion.answer.proofs.isEmpty())out.append("  No observed proof\n");
            }
            out.append('\n');
        }
        number=0;
        for(DmzSourcesQuery.Result stopped:family.uncovered) {
            out.append("UNCOVERED BRANCH ").append(++number).append(" — no completed opinion\n");prefix(out,stopped);out.append('\n');
        }
        number=0;
        for(DmzRestrictionFamily.Pending pending:family.pending) {
            out.append("PENDING GROUP ").append(++number).append(" — ").append(pending.reason==DmzRestrictionFamily.Stop.INVOCATION_LIMIT?"Invocation limit":"Choice limit")
                    .append("\nFirst unexplored choice: ").append(pending.nextChoice).append('\n');
            prefix(out,pending.parent);
            for(int i=pending.nextChoice-1;i<pending.parent.restrictionOutcome.availableChoices.size();++i) {
                out.append("Unexplored choice ").append(i+1).append(":\n");proof(out,pending.parent.restrictionOutcome.availableChoices.get(i),"  ");
            }
            out.append('\n');
        }
        out.append("Scope: qualified proof histories of this disposable unary-integer fixture.\n")
                .append("Equal answers can belong to different histories. Exhausted is not all-theories completeness.\n")
                .append("Reports contain historical observations, not replay permission. Retry with a fresh run and corrected limits.\n");
        return out.toString();
    }
    private static void prefix(StringBuilder out,DmzSourcesQuery.Result result) {
        out.append("Selected restrictions:\n");
        if(result.restrictionProofs.isEmpty())out.append("  None\n");
        for(DmzSourcesQuery.Proof selected:result.restrictionProofs)proof(out,selected,"  ");
        out.append("Observed conflicts:\n");
        for(DmzSourcesQuery.RestrictionStep step:result.restrictionOutcome.trace)
            for(DmzSourcesQuery.RestrictionConflict pair:step.conflicts) {
                out.append("  Step ").append(step.number).append(": ").append(pair.positive.conclusion).append(" / ").append(pair.negative.conclusion).append('\n');
                proof(out,pair.positive,"    ");proof(out,pair.negative,"    ");
            }
        for(DmzSourcesQuery.RestrictionConflict pair:result.restrictionOutcome.availableConflicts) {
            out.append("  Current: ").append(pair.positive.conclusion).append(" / ").append(pair.negative.conclusion).append('\n');
            proof(out,pair.positive,"    ");proof(out,pair.negative,"    ");
        }
    }
    private static void proof(StringBuilder out,DmzSourcesQuery.Proof proof,String indent) {
        out.append(indent).append("Conclusion: ").append(proof.conclusion).append('\n')
                .append(indent).append("Source rule: ").append(pin(proof.source.revision)).append(':').append(proof.source.rule).append('\n')
                .append(indent).append("Statement: ").append(proof.source.statement).append('\n')
                .append(indent).append("Exact identity: ").append(proof.identity()).append('\n');
        for(DmzSourcesQuery.Proof premise:proof.premises)proof(out,premise,indent+"  ");
    }
    private static String pin(IContextResults.Revision revision){return revision.getContextId()+"@"+revision.getRevision();}
    private static IContextResults.Revision create(Path dir,String name,String source) throws Exception {
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind mind=new Mind(user);user.setCurrentMind(mind);mind=(Mind)mind.useStorage(name);user.setCurrentMind(mind);
        try {require(mind.compile(source,null,false),"fixture "+name);}finally {user.setCurrentMind(mind.closeStorage());}
        try(ContextSnapshot snapshot=ContextSnapshot.open(dir.resolve(name))){return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());}
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
