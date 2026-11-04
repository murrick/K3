/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentModelSearchOwnershipRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("search-owning-").toString());ContextDependentModelSearchRunner.owning();System.out.println("CONTEXT_DEPENDENT_MODEL_SEARCH_OWNERSHIP_PASS checks="+ContextDependentModelSearchRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
