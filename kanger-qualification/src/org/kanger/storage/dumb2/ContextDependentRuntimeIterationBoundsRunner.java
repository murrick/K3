/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentRuntimeIterationBoundsRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("runtime-loop-bounds-").toString());ContextDependentRuntimeIterationRunner.bounds();System.out.println("CONTEXT_DEPENDENT_RUNTIME_ITERATION_BOUNDS_PASS checks="+ContextDependentRuntimeIterationRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
