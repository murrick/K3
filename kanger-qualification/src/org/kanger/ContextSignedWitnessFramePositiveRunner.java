/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessFramePositiveRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-frames-").toString());ContextSignedWitnessFrameMatrix.positive();System.out.println("CONTEXT_SIGNED_WITNESS_FRAME_POSITIVE_PASS checks="+ContextSignedWitnessFrameMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
