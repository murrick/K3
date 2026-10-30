/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessNextBoundaryRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-next-").toString());ContextSignedWitnessNextMatrix.boundaries();System.out.println("CONTEXT_SIGNED_WITNESS_NEXT_BOUNDARY_PASS checks="+ContextSignedWitnessNextMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
