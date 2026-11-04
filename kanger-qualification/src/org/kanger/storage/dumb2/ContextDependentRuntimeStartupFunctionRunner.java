/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentRuntimeStartupFunctionRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("runtime-startup-function-").toString());ContextDependentRuntimeStartupRunner.functions();System.out.println("CONTEXT_DEPENDENT_RUNTIME_STARTUP_FUNCTION_PASS checks="+ContextDependentRuntimeStartupRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
