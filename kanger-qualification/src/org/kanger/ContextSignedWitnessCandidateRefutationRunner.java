/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessCandidateRefutationRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-candidate-").toString());ContextSignedWitnessCandidateMatrix.refutations();System.out.println("CONTEXT_SIGNED_WITNESS_CANDIDATE_REFUTATION_PASS checks="+ContextSignedWitnessCandidateMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
