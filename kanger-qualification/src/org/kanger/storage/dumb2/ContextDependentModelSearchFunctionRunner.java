/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentModelSearchFunctionRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("search-functions-").toString());ContextDependentModelSearchRunner.functions();System.out.println("CONTEXT_DEPENDENT_MODEL_SEARCH_FUNCTION_PASS checks="+ContextDependentModelSearchRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
