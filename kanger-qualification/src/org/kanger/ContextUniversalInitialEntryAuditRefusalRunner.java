/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextUniversalInitialEntryAuditRefusalRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("initial-entry-audit-").toString());ContextUniversalInitialEntryAuditMatrix.refusals();System.out.println("MATRIX_SCOPES audits="+ContextUniversalContinuationMatrix.audits+" receiptRechecks="+ContextUniversalContinuationMatrix.receipts+" continuations="+ContextUniversalContinuationMatrix.continuations);System.out.println("CONTEXT_UNIVERSAL_INITIAL_ENTRY_AUDIT_REFUSAL_PASS checks="+ContextUniversalContinuationMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
