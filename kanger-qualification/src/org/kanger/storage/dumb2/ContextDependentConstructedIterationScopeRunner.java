/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextDependentConstructedIterationScopeRunner {
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("constructed-iteration-").toString());ContextDependentConstructedIterationRunner.scopes();ContextDependentConstructedIterationRunner.sourceRefusals();System.out.println("CONTEXT_DEPENDENT_CONSTRUCTED_ITERATION_SCOPE_PASS checks="+ContextDependentConstructedIterationRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
