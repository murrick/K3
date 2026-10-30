/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextSignedCommitGatesRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-commit-").toString());ContextSignedCommitMatrix.gates();System.out.println("CONTEXT_SIGNED_COMMIT_GATES_PASS checks="+ContextSignedCommitMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
