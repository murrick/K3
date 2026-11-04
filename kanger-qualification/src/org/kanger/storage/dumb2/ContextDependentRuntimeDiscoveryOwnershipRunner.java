/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentRuntimeDiscoveryOwnershipRunner {
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("runtime-discovery-owned-").toString());ContextDependentRuntimeDiscoveryRunner.owning();System.out.println("CONTEXT_DEPENDENT_RUNTIME_DISCOVERY_OWNERSHIP_PASS checks="+ContextDependentRuntimeDiscoveryRunner.checks);System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
