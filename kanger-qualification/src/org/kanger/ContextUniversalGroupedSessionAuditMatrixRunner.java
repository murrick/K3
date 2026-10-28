/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
/** Replay the existing independent chain/forgery matrix against the explicit session API. */
public final class ContextUniversalGroupedSessionAuditMatrixRunner {
    public static void main(String[] args){String before=System.getProperty("kanger.qualification.sessionAudit");try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("grouped-session-matrix-").toString());System.setProperty("kanger.qualification.sessionAudit","true");ContextUniversalGroupedChainAuditRunner.chain();ContextUniversalGroupedChainAuditRunner.symbolicAndRefutation();ContextUniversalGroupedChainAuditRunner.privateCase();System.out.println("CONTEXT_UNIVERSAL_GROUPED_SESSION_AUDIT_MATRIX_PASS checks="+ContextUniversalGroupedChainAuditRunner.checks);if(before==null)System.clearProperty("kanger.qualification.sessionAudit");else System.setProperty("kanger.qualification.sessionAudit",before);System.exit(0);}catch(Throwable e){if(before==null)System.clearProperty("kanger.qualification.sessionAudit");else System.setProperty("kanger.qualification.sessionAudit",before);e.printStackTrace();System.exit(1);}}
}
