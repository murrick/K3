/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFamilyInspection.Status;

/** Exact candidate staging, rebasing and all-value family inspection without publication. */
public final class ContextUniversalCandidateRunner {
    private static int checks,ids;
    private static final int WORK = 64000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static final class Fixture {
        final Mind mind; final Snapshot snapshot;
        Fixture(boolean x,String text) throws Exception {
            User u = new User(); mind = new Mind(u); u.setCurrentMind(mind);
            if (!text.isEmpty()) require(mind.compile(text,null,false),"native fixture");
            ContextProductionInventory i = ContextProductionInventory.inspect(mind,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
            ContextProductionNativeBinding.Result b = ContextProductionNativeBinding.bind(mind,i,WORK,500000);
            require(b.bound,"native binding"); snapshot = b.snapshot;
        }
        void unchanged() throws Exception {
            ContextProductionInventory i = snapshot.inventory;
            require(i.fingerprint.equals(ContextProductionInventory.inspect(mind,i.context,i.revision,i.localX,i.initialization,1000).fingerprint),"caller native inventory unchanged");
        }
    }
    private static ContextUniversalCandidate.Evaluation qualify(Fixture x,List<Snapshot> foreign,String text) {
        return ContextUniversalCandidate.qualify(x.snapshot,foreign,text,1000,8,16,16,20000,1000000,WORK,500000,WORK);
    }
    private static void incomplete(ContextUniversalCandidate.Evaluation e,String why) {
        require(e.status==Status.INCOMPLETE && (e.qualification==null || e.qualification.certificate==null && e.qualification.collision==null),why+": "+e.reason);
    }
    private static void threeVariables() throws Exception {
        Fixture x=new Fixture(true,""),a=new Fixture(false,"!seed(1,2,3);"),b=new Fixture(false,"!~ready(3,2,1);");
        String rule="!@x @y @z seed(x,y,z)->ready(z,y,x);";
        for(List<Snapshot> order:Arrays.asList(Arrays.asList(a.snapshot,b.snapshot),Arrays.asList(b.snapshot,a.snapshot))) {
            ContextUniversalCandidate.Evaluation e=qualify(x,order,rule);
            require(e.status==Status.COLLISION && e.qualification.completedBarriers==2,"three-variable consequence qualifies the opposite sibling at barrier2: "+e.reason);
            Set<ContextProductionCapture.Source> sources=new HashSet<>(Arrays.asList(e.artifact.candidate,a.snapshot.primary.get(0).source,b.snapshot.primary.get(0).source));
            require(e.qualification.collision.oppositions.stream().anyMatch(p -> p.support.sources.equals(sources) && p.support.depth==2),"all three exact Sources and cumulative depth survive permutation");
            ContextUniversalCandidateAudit.Audit audit=ContextUniversalCandidateAudit.audit(e,x.snapshot,order,rule,16,20000,WORK);
            require(audit.verified && audit.oppositions.stream().anyMatch(p -> p.usesCandidate && p.originalSources.equals(sources)),"independent auditor replays three-variable collision and candidate attribution");
        }
        Fixture edge=new Fixture(false,"!edge(1,2);"),different=new Fixture(false,"!~path(2,1);");
        String join="!@x @y @z edge(x,y)&&edge(y,z)->path(x,z);";
        ContextUniversalCandidate.Evaluation safe=qualify(x,Arrays.asList(edge.snapshot,different.snapshot),join);
        require(safe.status==Status.CONSISTENT && safe.qualification.certificate.cuts.size()==3,"variable positions are preserved, not collapsed: "+safe.reason);
        require(ContextUniversalCandidateAudit.audit(safe,x.snapshot,Arrays.asList(edge.snapshot,different.snapshot),join,16,20000,WORK).verified,"three-variable all-value model independently audited");
        incomplete(ContextUniversalCandidate.qualify(x.snapshot,Arrays.asList(edge.snapshot,different.snapshot),join,1000,8,16,16,1,1000000,WORK,500000,WORK),"three-variable grounding cap is incomplete, never approval");
        Fixture wideModel=new Fixture(false,"!~ready(3,1,2);");
        ContextUniversalCandidate.Evaluation capped=qualify(x,Arrays.asList(a.snapshot,wideModel.snapshot),rule);
        require(capped.status==Status.CONSISTENT,"previous ternary budget example completes with prefix pruning: "+capped.reason);
        require(ContextUniversalCandidateAudit.audit(capped,x.snapshot,Arrays.asList(a.snapshot,wideModel.snapshot),rule,16,20000,WORK).verified,"previous budget example still needs independent all-value audit");
        incomplete(ContextUniversalCandidate.qualify(x.snapshot,Arrays.asList(a.snapshot,wideModel.snapshot),rule,1000,8,16,16,20000,1,WORK,500000,WORK),"prefix pruning retains explicit model attempt cap");
        Fixture universal=new Fixture(false,"!@x @y @z seed(x,y,z);");
        ContextUniversalCandidate.Evaluation all=qualify(x,Collections.singletonList(universal.snapshot),rule);
        require(all.status==Status.CONSISTENT,"all-value three-variable universal consequence: "+all.reason);
        require(ContextUniversalCandidateAudit.audit(all,x.snapshot,Collections.singletonList(universal.snapshot),rule,16,20000,WORK).verified,"all-value certificate independently checked, not just mentioned tuples");
        x.unchanged();a.unchanged();b.unchanged();edge.unchanged();different.unchanged();wideModel.unchanged();universal.unchanged();
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-candidate-").toString());
            Fixture x=new Fixture(true,"!anchor(1);"),a=new Fixture(false,"!@x p(x);"),b=new Fixture(false,"!~q(9);");
            List<Snapshot> foreign=new ArrayList<>(Arrays.asList(a.snapshot,b.snapshot));
            ContextUniversalCandidate.Evaluation safe=qualify(x,foreign,"!@x p(x)->r(x);");
            require(safe.status==Status.CONSISTENT && safe.qualification.completedBarriers==2,"universal candidate requalifies generic r: "+safe.reason);
            require(safe.artifact.base==x.snapshot && safe.qualification.certificate.input.x==safe.artifact.staged,"certificate binds exact staged X, not original provider snapshot");
            require(safe.artifact.rebased.size()==x.snapshot.primary.size() && safe.artifact.rebased.containsKey(x.snapshot.primary.get(0).source),"every actual original Source has explicit staged rebasing");
            require(!safe.artifact.rebased.containsValue(safe.artifact.candidate) && safe.artifact.staged.primary.size()==x.snapshot.primary.size()+1,"candidate is exactly one distinct appended primary occurrence");
            require(safe.artifact.candidate.context.equals(x.snapshot.inventory.context) && safe.artifact.candidate.revision==x.snapshot.inventory.revision && safe.artifact.candidate.view.equals("inventory:"+safe.artifact.staged.inventory.fingerprint),"candidate retains actual context/revision with authenticated private view");
            require(safe.artifact.foreign.get(0)==a.snapshot && safe.artifact.foreign.get(1)==b.snapshot,"foreign pinned views never rebased");
            foreign.clear(); require(safe.artifact.foreign.size()==2,"caller list mutation cannot shrink staged family");
            ContextUniversalCandidate.Evaluation repeat=qualify(x,Arrays.asList(a.snapshot,b.snapshot),"!@x p(x)->r(x);");
            require(repeat.status==Status.CONSISTENT && !repeat.artifact.identity.equals(safe.artifact.identity) && !repeat.artifact.candidate.view.equals(safe.artifact.candidate.view),"repeat trial cannot reuse earlier candidate authority");
            ContextUniversalCandidate.Evaluation clash=qualify(x,Arrays.asList(a.snapshot,b.snapshot),"!@x p(x)->q(x);");
            require(clash.status==Status.COLLISION && clash.qualification.completedBarriers==2 && clash.qualification.certificate==null,"actual generic candidate conflicts at next complete barrier");
            Set<ContextProductionCapture.Source> causes=new HashSet<>(Arrays.asList(clash.artifact.candidate,a.snapshot.primary.get(0).source,b.snapshot.primary.get(0).source));
            require(clash.qualification.collision.oppositions.stream().anyMatch(p -> p.support.sources.equals(causes) && p.support.depth==2),"collision identifies exact staged candidate, original foreign Sources and cumulative depth2");
            Fixture empty=new Fixture(true,""),negative=new Fixture(false,"!@x @y ~p(x)||~p(y);");
            ContextUniversalCandidate.Evaluation factored=qualify(empty,Collections.singletonList(negative.snapshot),"!@x @y p(x)||p(y);");
            require(factored.status==Status.COLLISION && factored.qualification.completedBarriers==1,"factoring refutes actual universal candidate without synthetic anchor");
            require(factored.artifact.rebased.isEmpty() && factored.artifact.staged.primary.size()==1,"empty X stages exactly one real candidate");
            incomplete(qualify(x,Collections.emptyList(),"?p(1);"),"query rejected");
            incomplete(qualify(x,Collections.emptyList(),"!a(1); !b(1);"),"multi-assertion script rejected");
            incomplete(qualify(x,Collections.emptyList(),"!anchor(1);"),"duplicate/replacement outside append-only scope");
            incomplete(ContextUniversalCandidate.qualify(x.snapshot,Arrays.asList(a.snapshot,b.snapshot),"!@x p(x)->r(x);",1000,1,16,16,20000,1000000,WORK,500000,WORK),"changed common barrier cannot approve candidate");
            incomplete(ContextUniversalCandidate.qualify(x.snapshot,Collections.emptyList(),"!@x extra(x);",1000,8,16,16,20000,1000000,1,500000,WORK),"separate preparation allowance");
            ContextUniversalCandidate.Evaluation shortInference=ContextUniversalCandidate.qualify(x.snapshot,Collections.emptyList(),"!@x extra(x);",1000,8,16,16,20000,1000000,WORK,500000,1);
            incomplete(shortInference,"separate complete-inference allowance"); require(shortInference.artifact!=null && shortInference.qualification!=null,"successful private staging is historical only, not partial qualification evidence");
            boolean immutable=false; try { safe.artifact.rebased.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"staged mapping immutable");
            x.unchanged(); a.unchanged(); b.unchanged(); empty.unchanged(); negative.unchanged();
            threeVariables();
            System.out.println("CONTEXT_UNIVERSAL_CANDIDATE_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
