/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads the language-neutral Console/Browser vocabulary contract. */
public final class ClientVocabularyCorpus {

    private static final String RELATIVE =
            "kanger-qualification/test-data/client-vocabulary.tsv";

    private ClientVocabularyCorpus() {
    }

    public static List<Case> load() throws IOException {
        Path file = locate();
        List<Case> cases = new ArrayList<Case>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNumber += 1;
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 8) {
                throw new IOException("Invalid client vocabulary row "
                        + lineNumber + " in " + file + ": expected 8 fields");
            }
            boolean accepted;
            if ("ACCEPT".equals(fields[0])) {
                accepted = true;
            } else if ("REJECT".equals(fields[0])) {
                accepted = false;
            } else {
                throw new IOException("Invalid client vocabulary outcome at "
                        + lineNumber + " in " + file + ": " + fields[0]);
            }
            cases.add(new Case(
                    accepted,
                    fields[1],
                    fields[2],
                    value(fields[3]),
                    value(fields[4]),
                    value(fields[5]),
                    fields[6],
                    "yes".equals(fields[7])));
        }
        if (cases.isEmpty()) {
            throw new IOException("Client vocabulary corpus is empty: " + file);
        }
        return Collections.unmodifiableList(cases);
    }

    private static String value(String field) {
        return "-".equals(field) ? null : field;
    }

    private static Path locate() throws IOException {
        String root = System.getProperty("kanger.repo.root", "").trim();
        List<Path> candidates = new ArrayList<Path>();
        if (!root.isEmpty()) {
            candidates.add(Paths.get(root).resolve(RELATIVE));
        }
        candidates.add(Paths.get(RELATIVE));
        candidates.add(Paths.get("..").resolve(RELATIVE));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IOException("Client vocabulary corpus not found: " + RELATIVE);
    }

    public static final class Case {
        private final boolean accepted;
        private final String line;
        private final String result;
        private final String canonical;
        private final String argumentName;
        private final String argumentValue;
        private final String browserResult;
        private final boolean consoleSafe;

        private Case(boolean accepted,
                     String line,
                     String result,
                     String canonical,
                     String argumentName,
                     String argumentValue,
                     String browserResult,
                     boolean consoleSafe) {
            this.accepted = accepted;
            this.line = line;
            this.result = result;
            this.canonical = canonical;
            this.argumentName = argumentName;
            this.argumentValue = argumentValue;
            this.browserResult = browserResult;
            this.consoleSafe = consoleSafe;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getLine() {
            return line;
        }

        /** Accepted intent name or rejected parse reason name. */
        public String getResult() {
            return result;
        }

        public String getCanonical() {
            return canonical;
        }

        public String getArgumentName() {
            return argumentName;
        }

        public String getArgumentValue() {
            return argumentValue;
        }

        public String getBrowserResult() {
            return browserResult;
        }

        public boolean isConsoleSafe() {
            return consoleSafe;
        }

        @Override
        public String toString() {
            return (accepted ? "ACCEPT " : "REJECT ") + line;
        }
    }
}
