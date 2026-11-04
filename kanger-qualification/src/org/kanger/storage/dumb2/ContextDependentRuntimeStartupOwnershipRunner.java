/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentRuntimeStartupOwnershipRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("runtime-startup-ownership-").toString());ContextDependentRuntimeStartupRunner.owning();System.out.println("CONTEXT_DEPENDENT_RUNTIME_STARTUP_OWNERSHIP_PASS checks="+ContextDependentRuntimeStartupRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
