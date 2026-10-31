/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
public final class ContextSignedUnaryModelPositiveRunner {public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-unary-model-").toString());ContextSignedUnaryModelMatrix.positive();System.out.println("CONTEXT_SIGNED_UNARY_MODEL_POSITIVE_PASS checks="+ContextSignedUnaryModelMatrix.checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}}
