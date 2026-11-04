/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextDependentConstructedStartupOwnershipRunner {
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("constructed-startup-ownership-").toString());ContextDependentConstructedStartupRunner.owning();System.out.println("CONTEXT_DEPENDENT_CONSTRUCTED_STARTUP_OWNERSHIP_PASS checks="+ContextDependentConstructedStartupRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
