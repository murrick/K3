/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.Objects;
import org.kanger.interfaces.IContextResults.Revision;

/** Detached provider report of an actual explicit write attempt; never reusable authority. */
public final class ContextUniversalCandidatePublication {
    public enum Status { PUBLISHED, COLLISION, INCOMPLETE, NATIVE_REJECTED }
    public final Status status;
    public final ContextUniversalCandidate.Evaluation trial;
    public final ContextUniversalCandidateAudit.Audit audit;
    public final Revision before,after;
    public ContextUniversalCandidatePublication(Status status,ContextUniversalCandidate.Evaluation trial,
            ContextUniversalCandidateAudit.Audit audit,Revision before,Revision after) {
        this.status=Objects.requireNonNull(status); this.trial=Objects.requireNonNull(trial); this.audit=audit;
        this.before=Objects.requireNonNull(before); this.after=Objects.requireNonNull(after);
    }
}
