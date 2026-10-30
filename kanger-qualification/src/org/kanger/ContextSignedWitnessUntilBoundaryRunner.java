/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessUntilBoundaryRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-until-").toString());ContextSignedWitnessUntilMatrix.boundaries();System.out.println("CONTEXT_SIGNED_WITNESS_UNTIL_BOUNDARY_PASS checks="+ContextSignedWitnessUntilMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
