/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.ContextUniversalCandidateAudit.*;
import org.kanger.ContextProductionCapture.Source;

/** Rendering only. No inference, native input, publication or saved DMZ history mutation. */
final class ConsoleCandidateView {
    private ConsoleCandidateView() { }
    static void show(Audit audit) {
        ContextUniversalCandidate.Evaluation e=audit.evaluation;
        String status=audit.verified?e.status.name():"INCOMPLETE";
        System.out.println("Candidate check: "+status+" (read-only)");
        System.out.println("Reason: "+(e.status==ContextUniversalFamilyInspection.Status.INCOMPLETE?e.reason:audit.reason));
        evidence(e,audit);
        System.out.println("No assertion written. This result is historical and grants no write authority.");
    }
    static void write(ContextUniversalCandidatePublication report) {
        System.out.println("Candidate write: "+report.status);
        System.out.println("Physical revision: "+report.before.getContextId()+"@R"+report.before.getRevision()+" -> "
                +report.after.getContextId()+"@R"+report.after.getRevision());
        if(report.status==ContextUniversalCandidatePublication.Status.PUBLISHED) System.out.println("Assertion published.");
        else if(report.status==ContextUniversalCandidatePublication.Status.NATIVE_REJECTED) System.out.println("Native transaction rejected the assertion.");
        else System.out.println("Reason: "+(report.trial.status==ContextUniversalFamilyInspection.Status.INCOMPLETE?report.trial.reason:report.audit==null?report.trial.reason:report.audit.reason));
        evidence(report.trial,report.audit);
        if(report.status!=ContextUniversalCandidatePublication.Status.PUBLISHED) System.out.println("No assertion published.");
    }
    static void settlementFailure(org.kanger.exception.TransactionSettlementException settled) {
        System.err.println("Candidate write: "+settled.getOutcome()+"; finalization failed; reservation consumed="+settled.isReservationConsumed());
        System.err.println("Cause: "+(settled.getCause()==null?settled.toString():settled.getCause().toString()));
    }
    private static void evidence(ContextUniversalCandidate.Evaluation e,Audit audit) {
        System.out.println("Scope: flat ordinary universal clauses; all admissible values.");
        if(e.qualification!=null) System.out.println("Completed barriers: "+e.qualification.completedBarriers);
        if(e.artifact!=null) System.out.println("Context: "+e.artifact.base.inventory.context+"@R"+e.artifact.base.inventory.revision
                +"; cuts="+(e.artifact.foreign.size()+1));
        int n=0;
        for(Attribution opposition:audit==null?java.util.Collections.<Attribution>emptyList():audit.oppositions) {
            System.out.println("Collision proof "+(++n)+": uses candidate="+(opposition.usesCandidate?"yes":"no")+"; depth="+opposition.proof.support.depth);
            for(Source source:opposition.originalSources) System.out.println("  "+(source.equals(e.artifact.candidate)?"candidate":source.localX?"original X":"foreign")
                    +" "+source.context+"@R"+source.revision+":"+source.rule+" view="+source.view);
        }
    }
}
