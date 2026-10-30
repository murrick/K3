/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessStopTrustedRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-stop-").toString());ContextSignedWitnessStopMatrix.positiveTrusted();System.out.println("CONTEXT_SIGNED_WITNESS_STOP_TRUSTED_PASS checks="+ContextSignedWitnessStopMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
