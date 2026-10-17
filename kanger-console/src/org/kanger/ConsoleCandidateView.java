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
        System.out.println("Scope: flat ordinary universal clauses; all admissible values.");
        System.out.println("Reason: "+(e.status==ContextUniversalFamilyInspection.Status.INCOMPLETE?e.reason:audit.reason));
        if(e.qualification!=null) System.out.println("Completed barriers: "+e.qualification.completedBarriers);
        if(e.artifact!=null) System.out.println("Context: "+e.artifact.base.inventory.context+"@R"+e.artifact.base.inventory.revision
                +"; cuts="+(e.artifact.foreign.size()+1));
        int n=0;
        for(Attribution opposition:audit.oppositions) {
            System.out.println("Collision proof "+(++n)+": uses candidate="+(opposition.usesCandidate?"yes":"no")+"; depth="+opposition.proof.support.depth);
            for(Source source:opposition.originalSources) System.out.println("  "+(source.equals(e.artifact.candidate)?"candidate":source.localX?"original X":"foreign")
                    +" "+source.context+"@R"+source.revision+":"+source.rule+" view="+source.view);
        }
        System.out.println("No assertion written. This result is historical and grants no write authority.");
    }
}
