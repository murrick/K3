/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessInitialPositiveRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-initial-").toString());ContextSignedWitnessInitialMatrix.positive();System.out.println("CONTEXT_SIGNED_WITNESS_INITIAL_POSITIVE_PASS checks="+ContextSignedWitnessInitialMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
