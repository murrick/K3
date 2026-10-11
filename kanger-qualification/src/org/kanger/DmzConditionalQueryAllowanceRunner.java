/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;
/** Exact discovery allowance must not be charged again as unstarted family work. */
public final class DmzConditionalQueryAllowanceRunner {
 public static void main(String[] args) {
  try { run(); System.out.println("DMZ_CONDITIONAL_EXACT_ALLOWANCE_PASS checks=4"); System.exit(0); }
  catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
 }
 private static void run() throws Exception {
  User u=new User(); new UDF().init(u); Mind q=new Mind(u); u.setCurrentMind(q);
  IContextResults.Revision src=new IContextResults.Revision(UUID.randomUUID(),1);
  try(DmzReplayProvenance j=DmzReplayProvenance.begin(); TerminalSupportCapture c=TerminalSupportCapture.begin(q)) {
   DmzReplayProvenance.acceptRule(q,src,1,DmzReplayProvenance.Authority.EXTERNAL,"!@x source(x) -> middle(x);");
   DmzReplayProvenance.acceptRule(q,src,2,DmzReplayProvenance.Authority.EXTERNAL,"!@x middle(x) -> derived(x);");
   DmzAcceptanceProofGuard g=DmzAcceptanceProofGuard.beforeInput(c,j,q);
   g.accept(src,3,DmzReplayProvenance.Authority.EXTERNAL,"!source(1);");
   TerminalSupportCapture.Materialization mat=null;
   for(TerminalSupportCapture.StoredObservation o:c.storedSnapshot())
    if(o.materialization.causes.nodes.get(o.materialization.causes.root).ground.predicate.equals("derived")) mat=o.materialization;
   DmzStoredProof p=DmzStoredProof.build(c,j,mat,10000);
   try(DmzStoredRetractionTransaction o=DmzStoredRetractionTransaction.beginContinuation(g,p,p.witnesses.get(0),q,10000)) {
    o.accept(src,4,DmzReplayProvenance.Authority.EXTERNAL,"!source(2);");
    String candidate="!~derived(2);", query="?source(1);";
    DmzStoredRetractionTransaction.SymmetricProjection projection=o.projectSymmetric(o.conflictEvidence(candidate,10000),10000);
    try(DmzStoredRetractionTransaction.NativeSupportChoice choice=o.beginSupportChoice(projection,DmzStoredRetractionTransaction.SupportChoice.KEEP_CANDIDATE,10000)) {
     DmzStoredRetractionTransaction.ConditionalQueryResult measured=choice.executeConditionalQuery(src,150,candidate,query,10000,10000,10000,10000,10000,4,1,1000000);
     int cost=measured.traversalWork;
     DmzStoredRetractionTransaction.ConditionalQueryResult exact=choice.executeConditionalQuery(src,150,candidate,query,10000,10000,10000,10000,10000,4,64,cost);
     require(exact.family!=null && exact.family.unstartedRoot!=null && exact.family.workLimit==0 && exact.family.traversalWork==0 && exact.traversalWork==cost && projection.isCurrent());
     DmzStoredRetractionTransaction.ConditionalQueryResult plus=choice.executeConditionalQuery(src,150,candidate,query,10000,10000,10000,10000,10000,4,64,cost+1);
     require(plus.family!=null && plus.family.unstartedRoot!=null && plus.family.traversalWork==1 && plus.traversalWork==cost+1 && projection.isCurrent());
    }
    DmzStoredRetractionTransaction.CandidateEvaluation measured=o.evaluateCandidate(src,150,candidate,query,10000,10000,new DmzStoredRetractionTransaction.ConditionalQueryLimits(10000,10000,10000,10000,4,1,1000000));
    int cost=measured.conditional.traversalWork;
    DmzStoredRetractionTransaction.CandidateEvaluation exact=o.evaluateCandidate(src,150,candidate,query,10000,10000,new DmzStoredRetractionTransaction.ConditionalQueryLimits(10000,10000,10000,10000,4,64,cost));
    require(exact.conditional.family!=null && exact.conditional.family.traversalWork==0 && exact.conditional.family.workLimit==0 && exact.conditional.traversalWork==cost && exact.conditional.restartRequired && projection.isCurrent());
   }
   require(Boolean.TRUE.equals(q.query("?source(1);",null,false)));
  }
 }
 static void require(boolean v) { if(!v) throw new AssertionError("Exact allowance accounting and rollback"); }
}
