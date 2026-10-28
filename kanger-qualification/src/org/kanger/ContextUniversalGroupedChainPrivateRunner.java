/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
/** Separate actual private-parameter chain replay to retain bounded per-driver qualification. */
public final class ContextUniversalGroupedChainPrivateRunner {
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("grouped-chain-private-").toString());ContextUniversalGroupedChainAuditRunner.privateCase();System.out.println("CONTEXT_UNIVERSAL_GROUPED_CHAIN_PRIVATE_PASS checks="+ContextUniversalGroupedChainAuditRunner.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
