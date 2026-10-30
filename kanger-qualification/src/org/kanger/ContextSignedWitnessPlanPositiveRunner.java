/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedWitnessPlanPositiveRunner {
 public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-plan-").toString());ContextSignedWitnessPlanMatrix.positive();System.out.println("CONTEXT_SIGNED_WITNESS_PLAN_POSITIVE_PASS checks="+ContextSignedWitnessPlanMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
