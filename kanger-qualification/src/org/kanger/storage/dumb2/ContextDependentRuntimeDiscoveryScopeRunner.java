/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentRuntimeDiscoveryScopeRunner {
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("runtime-discovery-scopes-").toString());ContextDependentRuntimeDiscoveryRunner.functions();ContextDependentRuntimeDiscoveryRunner.privateContinuation();System.out.println("CONTEXT_DEPENDENT_RUNTIME_DISCOVERY_SCOPE_PASS checks="+ContextDependentRuntimeDiscoveryRunner.checks);System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
