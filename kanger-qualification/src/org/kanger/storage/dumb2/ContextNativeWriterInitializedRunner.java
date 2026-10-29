/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextNativeWriterInitializedRunner {
 public static void main(String[] args){try{ContextNativeWriterCompatibilityMatrix.initialized();System.out.println("CONTEXT_NATIVE_WRITER_INITIALIZED_PASS checks="+ContextNativeWriterCompatibilityMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
