/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.jline.reader.EOFError;
import org.jline.reader.EndOfFileException;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IUser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * Interactive terminal boundary for the standalone Java Console.
 *
 * <p>JLine owns physical terminal editing and history navigation. KANGER owns
 * only the logical-input completeness rule used to decide whether Enter ends
 * the current operation or opens a continuation line. Command grammar remains
 * exclusively in kanger-command.</p>
 */
final class ConsoleLineInput implements AutoCloseable {

    static final String HISTORY_SIZE_PROPERTY = "console.history.size";
    static final int DEFAULT_HISTORY_SIZE = 1000;
    static final String HISTORY_FILE_NAME = "console.history";

    private final Terminal terminal;
    private final LineReader reader;
    private final DefaultHistory history;

    private ConsoleLineInput(Terminal terminal,
                             LineReader reader,
                             DefaultHistory history) {
        this.terminal = terminal;
        this.reader = reader;
        this.history = history;
    }

    static ConsoleLineInput open(IUser user) throws Exception {
        int historySize = historySize(user);
        Path historyFile = Paths.get(user.getUserDir(), HISTORY_FILE_NAME);
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        DefaultHistory history = new DefaultHistory();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new KangerInputParser())
                .history(history)
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, "  ")
                .variable(LineReader.HISTORY_FILE, historyFile)
                .variable(LineReader.HISTORY_SIZE, historySize)
                .variable(LineReader.HISTORY_FILE_SIZE, historySize)
                .build();
        return new ConsoleLineInput(terminal, reader, history);
    }

    String readCommand() throws IOException {
        try {
            String line = reader.readLine("\n: ");
            return normalize(line);
        } catch (UserInterruptException ex) {
            return null;
        } catch (EndOfFileException ex) {
            return "quit";
        }
    }

    String readAuxiliary(String prompt) {
        Object previous = reader.getVariable(LineReader.DISABLE_HISTORY);
        reader.setVariable(LineReader.DISABLE_HISTORY, Boolean.TRUE);
        try {
            return reader.readLine(prompt);
        } catch (UserInterruptException ex) {
            return "";
        } catch (EndOfFileException ex) {
            return "";
        } finally {
            reader.setVariable(LineReader.DISABLE_HISTORY, previous);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            history.save();
        } finally {
            terminal.close();
        }
    }

    static boolean isComplete(String line) {
        if (line == null) {
            return true;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return true;
        }

        String lineStart = trimmed.length() >= 2
                && (trimmed.startsWith("//") || trimmed.startsWith("/*"))
                ? trimmed.substring(0, 2)
                : trimmed.substring(0, 1);
        String lineStop = trimmed.length() >= 2 && trimmed.endsWith("*/")
                ? "*/"
                : trimmed.substring(trimmed.length() - 1);

        if ("/*".equals(lineStart)) {
            return "*/".equals(lineStop);
        }
        if ("=".equals(lineStart)) {
            return hasBlankPhysicalTail(line);
        }
        if (!"?".equals(trimmed)
                && "!?+-=".contains(lineStart.substring(0, 1))) {
            return ";".equals(lineStop);
        }
        return true;
    }

    private static boolean hasBlankPhysicalTail(String line) {
        int newline = line.lastIndexOf('\n');
        return newline >= 0 && line.substring(newline + 1).trim().isEmpty();
    }

    private static int historySize(IUser user) throws Exception {
        String value = user.getProperty(HISTORY_SIZE_PROPERTY,
                Integer.toString(DEFAULT_HISTORY_SIZE));
        try {
            int size = Integer.parseInt(value.trim());
            if (size <= 0) {
                throw new NumberFormatException();
            }
            return size;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(HISTORY_SIZE_PROPERTY
                    + " must be a positive integer: " + value);
        }
    }

    private static String normalize(String line) {
        if ("\n".equals(Enums.LINE_SEPARATOR)) {
            return line;
        }
        return line.replace("\n", Enums.LINE_SEPARATOR);
    }

    private static final class KangerInputParser implements Parser {
        @Override
        public ParsedLine parse(String line, int cursor, ParseContext context)
                throws SyntaxError {
            if (context == ParseContext.ACCEPT_LINE && !isComplete(line)) {
                throw new EOFError(-1, -1, "Incomplete KANGER input");
            }
            return new WholeLine(line, cursor);
        }
    }

    private static final class WholeLine implements ParsedLine {
        private final String line;
        private final int cursor;

        private WholeLine(String line, int cursor) {
            this.line = line == null ? "" : line;
            this.cursor = cursor;
        }

        @Override
        public String word() {
            return line;
        }

        @Override
        public int wordCursor() {
            return cursor;
        }

        @Override
        public int wordIndex() {
            return 0;
        }

        @Override
        public List<String> words() {
            return Collections.singletonList(line);
        }

        @Override
        public String line() {
            return line;
        }

        @Override
        public int cursor() {
            return cursor;
        }
    }
}
