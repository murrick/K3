/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextNativeWriterBoundaryRunner {
 public static void main(String[] args){try{ContextNativeWriterMatrix.boundaries();System.out.println("CONTEXT_NATIVE_WRITER_BOUNDARY_PASS checks="+ContextNativeWriterMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
