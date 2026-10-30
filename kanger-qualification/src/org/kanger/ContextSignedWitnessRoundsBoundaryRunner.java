/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessRoundsBoundaryRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-rounds-").toString());ContextSignedWitnessRoundsMatrix.boundaries();System.out.println("CONTEXT_SIGNED_WITNESS_ROUNDS_BOUNDARY_PASS checks="+ContextSignedWitnessRoundsMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
