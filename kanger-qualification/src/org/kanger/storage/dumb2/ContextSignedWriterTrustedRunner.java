/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextSignedWriterTrustedRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-writer-").toString());ContextSignedWriterMatrix.trusted();System.out.println("CONTEXT_SIGNED_WRITER_TRUSTED_PASS checks="+ContextSignedWriterMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
