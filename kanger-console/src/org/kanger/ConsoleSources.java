/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.kanger.command.SourceNamePolicy;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ILogEntry;

/** Console source-file operations; prompts and parse diagnostics belong to the session. */
final class ConsoleSources {
    private ConsoleSources() {
    }

    static void showSourceNames(IMind mind) {
        File[] files = new File(mind.getUser().getSourceDir()).listFiles();
        List<String> names = new ArrayList<String>();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()
                        && SourceNamePolicy.isCanonicalSourceFileName(file.getName())) {
                    names.add(file.getName());
                }
            }
        }
        Collections.sort(names);
        if (names.isEmpty()) {
            System.out.println("No source files available");
            return;
        }
        System.out.println("Source files available:");
        for (String name : names) {
            System.out.println("\t" + name);
        }
    }

    private static File sourceFile(IMind mind, String name) throws Exception {
        File root = new File(mind.getUser().getSourceDir()).getCanonicalFile();
        File file = new File(root, name).getCanonicalFile();
        if (!root.equals(file.getParentFile())) {
            throw new CommandErrorException("Invalid source name " + name);
        }
        return file;
    }

    static IMind loadSource(IMind mind,
                                    String name,
                                    java.util.function.Consumer<String> parseSource) throws Exception {
        File file = sourceFile(mind, name);
        if (!file.isFile()) {
            System.out.println("WARNING: File " + name + " not found");
            return mind;
        }
        if (file.length() == 0L) {
            System.out.println("WARNING: File " + name + " is empty");
            return mind;
        }

        String text = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        parseSource.accept(text);
        boolean accepted = mind.compile(text);
        if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) == 0) {
            ILogEntry log = mind.getCurrentLogRecord(LogMode.ANALYZER);
            if (log != null) {
                System.out.println(log.getRecord());
            }
        }
        if (accepted) {
            System.out.println("File " + file.getName() + " loaded");
        } else {
            showCompileCollisions(mind);
            System.out.println("Use xplain for analysis");
        }
        return mind;
    }

    private static void showCompileCollisions(IMind mind) {
        if (!(mind instanceof Mind)) {
            return;
        }
        ContextQualification qualification =
                ((Mind) mind).getLastCompileQualification();
        if (qualification == null || qualification.isValid()) {
            return;
        }
        for (ContextQualification.CollisionWitness witness
                : qualification.getCollisions()) {
            System.out.printf("  collision: %s <> %s%n",
                    witness.getLeft(), witness.getRight());
        }
    }

    static void saveSource(IMind mind, String name, java.util.function.Predicate<String> confirm) throws Exception {
        File file = sourceFile(mind, name);
        if (file.exists() && !confirm.test("Overwrite source file " + name + "?")) {
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.write(SourceContextMaterializer.materializeCurrentLevel(mind));
        }
        System.out.println("Source file " + name + " saved.");
    }

    static void deleteSource(IMind mind, String name, java.util.function.Predicate<String> confirm) throws Exception {
        File file = sourceFile(mind, name);
        if (!file.exists()) {
            System.out.println("Source file " + name + " not found");
            return;
        }
        if (!confirm.test("Delete source file " + name + "?")) {
            return;
        }
        if (!file.delete()) {
            throw new CommandErrorException("Cannot delete source file " + name);
        }
        System.out.println("Source file " + name + " deleted.");
    }

}
