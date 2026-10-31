/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedUnaryBranchIntegrityRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-final-model-").toString());ContextSignedUnaryBranchMatrix.integrity();System.out.println("CONTEXT_SIGNED_UNARY_BRANCH_INTEGRITY_PASS checks="+ContextSignedUnaryBranchMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
