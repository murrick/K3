/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessGroupedBoundaryRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-grouped-").toString());ContextSignedWitnessGroupedMatrix.boundaries();System.out.println("CONTEXT_SIGNED_WITNESS_GROUPED_BOUNDARY_PASS checks="+ContextSignedWitnessGroupedMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
