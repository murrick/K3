/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentModelSearchScopeRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("search-scope-").toString());ContextDependentModelSearchRunner.scopes();System.out.println("CONTEXT_DEPENDENT_MODEL_SEARCH_SCOPE_PASS checks="+ContextDependentModelSearchRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
