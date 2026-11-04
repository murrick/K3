/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentRuntimeIterationOwnershipRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("runtime-loop-ownership-").toString());ContextDependentRuntimeIterationRunner.owning();System.out.println("CONTEXT_DEPENDENT_RUNTIME_ITERATION_OWNERSHIP_PASS checks="+ContextDependentRuntimeIterationRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
