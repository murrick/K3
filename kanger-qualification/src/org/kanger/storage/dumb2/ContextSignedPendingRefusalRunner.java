/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextSignedPendingRefusalRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-pending-").toString());ContextSignedPendingMatrix.refusal();System.out.println("CONTEXT_SIGNED_PENDING_REFUSAL_PASS checks="+ContextSignedPendingMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
