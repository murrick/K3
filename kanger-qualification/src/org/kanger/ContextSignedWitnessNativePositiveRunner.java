/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessNativePositiveRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-native-").toString());ContextSignedWitnessNativeMatrix.positive();System.out.println("CONTEXT_SIGNED_WITNESS_NATIVE_POSITIVE_PASS checks="+ContextSignedWitnessNativeMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
