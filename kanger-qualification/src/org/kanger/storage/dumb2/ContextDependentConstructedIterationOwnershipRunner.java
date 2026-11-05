/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextDependentConstructedIterationOwnershipRunner {
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("constructed-iteration-").toString());ContextDependentConstructedIterationRunner.owning();System.out.println("CONTEXT_DEPENDENT_CONSTRUCTED_ITERATION_OWNERSHIP_PASS checks="+ContextDependentConstructedIterationRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
