/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentModelSearchBoundsRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("search-bounds-").toString());ContextDependentModelSearchRunner.bounds();System.out.println("CONTEXT_DEPENDENT_MODEL_SEARCH_BOUNDS_PASS checks="+ContextDependentModelSearchRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
