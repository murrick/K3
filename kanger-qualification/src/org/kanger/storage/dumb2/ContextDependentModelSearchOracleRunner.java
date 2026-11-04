/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.Files;
public final class ContextDependentModelSearchOracleRunner {
 public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("search-oracle-").toString());ContextDependentModelSearchRunner.oracle();System.out.println("CONTEXT_DEPENDENT_MODEL_SEARCH_ORACLE_PASS checks="+ContextDependentModelSearchRunner.checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
