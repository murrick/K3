/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextSignedWriterForwardRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-writer-").toString());ContextSignedWriterMatrix.positive(false);System.out.println("CONTEXT_SIGNED_WRITER_FORWARD_PASS checks="+ContextSignedWriterMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
