/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
/** Isolated automatic grouped round qualification; shared fixtures stay in the owning runner. */
public final class ContextUniversalGroupedRoundsRefutationRunner {
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("grouped-rounds-isolated-").toString());
        ContextUniversalGroupedRoundsRunner.rejectionAndAllChecks();
        System.out.println("CONTEXT_UNIVERSAL_GROUPED_ROUNDS_REFUTATION_PASS checks="+ContextUniversalGroupedRoundsRunner.checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
