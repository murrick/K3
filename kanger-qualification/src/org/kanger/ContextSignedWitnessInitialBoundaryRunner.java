/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessInitialBoundaryRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-initial-").toString());ContextSignedWitnessInitialMatrix.boundaries();System.out.println("CONTEXT_SIGNED_WITNESS_INITIAL_BOUNDARY_PASS checks="+ContextSignedWitnessInitialMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
