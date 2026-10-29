/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextNativeWriterLegacyRunner {
 public static void main(String[] args){try{ContextNativeWriterCompatibilityMatrix.legacy();System.out.println("CONTEXT_NATIVE_WRITER_LEGACY_PASS checks="+ContextNativeWriterCompatibilityMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
