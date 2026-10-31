/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedEligibilityCoverageRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-eligibility-").toString());ContextSignedEligibilityMatrix.coverage();System.out.println("CONTEXT_SIGNED_ELIGIBILITY_COVERAGE_PASS checks="+ContextSignedEligibilityMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
