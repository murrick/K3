/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
/** Isolated signed rejection for multi-Source private symbolic results. */
public final class ContextUniversalPrivateSymbolicRefutationRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("private-symbolic-refutation-").toString());ContextUniversalPrivateSymbolicMatrix.refutedCases();System.out.println("CONTEXT_UNIVERSAL_PRIVATE_SYMBOLIC_REFUTATION_PASS checks="+ContextUniversalContinuationMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
