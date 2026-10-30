/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextSignedCandidateLeaseFreshnessRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-lease-").toString());ContextSignedCandidateLeaseMatrix.freshness();System.out.println("CONTEXT_SIGNED_CANDIDATE_LEASE_FRESHNESS_PASS checks="+ContextSignedCandidateLeaseMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
