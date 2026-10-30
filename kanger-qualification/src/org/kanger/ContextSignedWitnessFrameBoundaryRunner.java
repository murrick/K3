/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessFrameBoundaryRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-frames-").toString());ContextSignedWitnessFrameMatrix.boundaries();System.out.println("CONTEXT_SIGNED_WITNESS_FRAME_BOUNDARY_PASS checks="+ContextSignedWitnessFrameMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
