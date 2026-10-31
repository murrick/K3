/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import org.kanger.interfaces.IContextResults.Revision;
/** Detached report of an ACTUAL fresh write, never reusable admission authority. */
public final class ContextSignedModelPublication {
 public enum Status { PUBLISHED,NATIVE_REJECTED,REFUTED,INCOMPLETE }
 public final Status status;public final String phase,reason;public final Revision before,after;public final ContextSignedPendingGuard.Evidence evidence;
 public ContextSignedModelPublication(Status status,String phase,String reason,Revision before,Revision after,ContextSignedPendingGuard.Evidence evidence){this.status=java.util.Objects.requireNonNull(status);this.phase=phase;this.reason=reason;this.before=java.util.Objects.requireNonNull(before);this.after=java.util.Objects.requireNonNull(after);this.evidence=evidence;}
 public String description(){return "MODEL WRITE: "+status+"\n"+(status==Status.PUBLISHED?"Revision "+before.getRevision()+" -> "+after.getRevision():reason);}
}
