/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessReceiverPositiveRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-receiver-").toString());ContextSignedWitnessReceiverMatrix.positive();System.out.println("CONTEXT_SIGNED_WITNESS_RECEIVER_POSITIVE_PASS checks="+ContextSignedWitnessReceiverMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
