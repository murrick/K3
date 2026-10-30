/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessBarrierPositiveRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-barrier-").toString());ContextSignedWitnessBarrierMatrix.positive();System.out.println("CONTEXT_SIGNED_WITNESS_BARRIER_POSITIVE_PASS checks="+ContextSignedWitnessBarrierMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
