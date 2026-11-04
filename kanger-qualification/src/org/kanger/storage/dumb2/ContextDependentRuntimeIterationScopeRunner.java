/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentRuntimeIterationScopeRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("runtime-loop-scope-").toString());ContextDependentRuntimeIterationRunner.privateContinuation();ContextDependentRuntimeIterationRunner.changedChoices();System.out.println("CONTEXT_DEPENDENT_RUNTIME_ITERATION_SCOPE_PASS checks="+ContextDependentRuntimeIterationRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
