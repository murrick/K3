/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import java.io.File;
import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.IMind;

/** Console-local bridge to the optional, isolated qualification runtime. */
final class ConsoleTestCommand {

    private ConsoleTestCommand() {
    }

    /**
     * Console-only developer hook. Deliberately bypasses the canonical command
     * grammar and is intentionally absent from help/documentation.
     */
    static boolean matches(String line) {
        String[] parts = line.trim().split("\\s+");
        return parts.length >= 2
                && ("options".equalsIgnoreCase(parts[0]) || "opt".equalsIgnoreCase(parts[0]))
                && "test".equalsIgnoreCase(parts[1]);
    }

    static void run(String line, IMind mind) throws Exception {
        String[] parts = line.trim().split("\\s+");
        if (parts.length > 3) {
            throw new CommandErrorException("Invalid options test syntax");
        }
        String prefix = parts.length == 3 ? parts[2] : "";

        /*
         * Do not lend the live Console Mind/User/storage to the historical test
         * corpus. The qualification runtime creates a disposable User + Mind and,
         * when the current Console is database-backed, a private temporary database of the selected backend. This preserves the live transaction stack and storage exactly.
         *
         * Reflection keeps the production Console independent of the qualification
         * module. The hidden command exists only when that developer/test plane is
         * present on the runtime class path.
         */
        java.net.URLClassLoader developerLoader = null;
        try {
            Class<?> runtime;
            try {
                runtime = Class.forName("org.kanger.IsolatedKangerTestRuntime");
            } catch (ClassNotFoundException missingFromRuntime) {
                File directory = new File(System.getProperty("user.dir", "."))
                        .getCanonicalFile();
                File classes = null;
                for (int depth = 0; depth < 5 && directory != null; ++depth) {
                    File candidate = new File(directory,
                            "kanger-qualification/target/test-classes");
                    File marker = new File(candidate,
                            "org/kanger/IsolatedKangerTestRuntime.class");
                    if (marker.isFile()) {
                        classes = candidate;
                        break;
                    }
                    directory = directory.getParentFile();
                }
                if (classes == null) {
                    throw missingFromRuntime;
                }
                developerLoader = new java.net.URLClassLoader(
                        new java.net.URL[]{classes.toURI().toURL()},
                        ConsoleTestCommand.class.getClassLoader());
                runtime = Class.forName(
                        "org.kanger.IsolatedKangerTestRuntime",
                        true,
                        developerLoader);
            }

            java.lang.reflect.Method run =
                    runtime.getDeclaredMethod("run", String.class, String.class);
            run.setAccessible(true);
            String storageClass = mind.isStorageUsed()
                    ? ((User) mind.getUser()).getData().getClass().getName() : null;
            Object result = run.invoke(null, prefix, storageClass);
            if (!(result instanceof Boolean) || !((Boolean) result).booleanValue()) {
                throw new CommandErrorException("KANGER test failed");
            }
        } catch (ClassNotFoundException ex) {
            throw new CommandErrorException(
                    "Console test runtime is unavailable; compile kanger-qualification first");
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        } finally {
            if (developerLoader != null) {
                developerLoader.close();
            }
        }
    }

}
