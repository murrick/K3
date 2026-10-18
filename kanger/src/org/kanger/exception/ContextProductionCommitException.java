/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.exception;
import java.util.*;
import org.kanger.*;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Pre-settlement whole-family refusal. Diagnostic evidence never grants publication. */
public final class ContextProductionCommitException extends StorageLifecycleException {
    private final ContextUniversalFamilyAudit.Audit audit;
    private ContextProductionCommitException(StorageLifecycleErrorCode code,String reason,ContextUniversalFamilyAudit.Audit audit,List<ContextQualification.CollisionWitness> witnesses) {
        super(code,reason,witnesses);this.audit=audit;
    }
    public ContextUniversalFamilyAudit.Audit getFamilyAudit() {return audit;}
    public static ContextProductionCommitException incomplete(String reason,ContextUniversalFamilyAudit.Audit audit) {
        return new ContextProductionCommitException(StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,reason,audit,Collections.emptyList());
    }
    public static ContextProductionCommitException collision(ContextUniversalFamilyAudit.Audit audit) {
        if(audit==null || !audit.verified || audit.verdict.status!=ContextUniversalFamilyInspection.Status.COLLISION || audit.oppositions.isEmpty()) throw new IllegalArgumentException("Verified complete family collision required");
        List<ContextQualification.CollisionWitness> witnesses=new ArrayList<>();
        for(ContextUniversalUnitProof.Proof p:audit.oppositions) witnesses.add(ContextQualification.witness(p.left.assertion(),p.right.assertion()));
        return new ContextProductionCommitException(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"Proposed Context family has an independently audited production collision",audit,witnesses);
    }
}
