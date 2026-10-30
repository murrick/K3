/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessPlanBoundaryRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-plan-").toString());ContextSignedWitnessPlanMatrix.boundaries();System.out.println("CONTEXT_SIGNED_WITNESS_PLAN_BOUNDARY_PASS checks="+ContextSignedWitnessPlanMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
