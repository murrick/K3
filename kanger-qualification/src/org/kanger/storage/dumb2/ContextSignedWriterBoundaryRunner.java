/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
public final class ContextSignedWriterBoundaryRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-writer-").toString());ContextSignedWriterMatrix.boundary();System.out.println("CONTEXT_SIGNED_WRITER_BOUNDARY_PASS checks="+ContextSignedWriterMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
