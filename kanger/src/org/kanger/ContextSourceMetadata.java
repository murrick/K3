/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.internal.IContextFederation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Closed declarative metadata vocabulary embedded in canonical .k source.
 *
 * <p>These lines are source metadata, never Console commands. They are removed
 * from Core compiler input while preserving source offsets.</p>
 */
final class ContextSourceMetadata {

    private static final String MARKER = "//!";
    private static final String CONTEXT_PREFIX = "//! ctx ";

    private ContextSourceMetadata() {
    }

    static Parsed parse(String source) {
        String input = source == null ? "" : source;
        StringBuilder stripped =
                new StringBuilder(input.length());
        ArrayList<IContextFederation.SourceDependencyRequest>
                requests =
                new ArrayList<IContextFederation.SourceDependencyRequest>();
        boolean present = false;
        boolean explicitNone = false;

        int start = 0;
        while (start < input.length()) {
            int newline = input.indexOf('\n', start);
            int end = newline < 0
                    ? input.length()
                    : newline + 1;
            String line = input.substring(start, end);
            String logical = line;
            while (!logical.isEmpty()) {
                char tail =
                        logical.charAt(
                                logical.length() - 1);
                if (tail == '\n' || tail == '\r') {
                    logical =
                            logical.substring(
                                    0,
                                    logical.length() - 1);
                } else {
                    break;
                }
            }

            String trimmed = logical.trim();
            if (trimmed.startsWith(CONTEXT_PREFIX)) {
                present = true;
                String directive =
                        trimmed.substring(
                                MARKER.length())
                                .trim();
                if ("ctx dependencies none"
                        .equals(directive)) {
                    if (explicitNone
                            || !requests.isEmpty()) {
                        throw invalid(
                                "ctx dependencies none cannot be combined with another dependency directive");
                    }
                    explicitNone = true;
                } else if (directive.startsWith(
                        "ctx connect ")) {
                    if (explicitNone) {
                        throw invalid(
                                "ctx connect cannot follow ctx dependencies none");
                    }
                    String operand =
                            directive.substring(
                                    "ctx connect ".length())
                                    .trim();
                    requests.add(
                            parseDependency(operand));
                } else if (directive.startsWith("ctx init+ ")) {
                    if (requests.isEmpty()) throw invalid("ctx init+ requires a preceding initialization command");
                    int last = requests.size() - 1;
                    IContextFederation.SourceDependencyRequest dependency = requests.get(last);
                    List<String> commands = new ArrayList<String>(dependency.getInitialization());
                    if (commands.isEmpty()) throw invalid("ctx init+ requires a preceding initialization command");
                    int commandIndex = commands.size() - 1;
                    commands.set(commandIndex, commands.get(commandIndex) + "\n" + directive.substring("ctx init+ ".length()));
                    requests.set(last, new IContextFederation.SourceDependencyRequest(dependency.getLocator(),
                            dependency.isExact() ? dependency.getExactRevision() : null, commands, dependency.getTrustGroup()));
                } else if (directive.startsWith("ctx init ")) {
                    if (explicitNone || requests.isEmpty()) throw invalid("ctx init requires a preceding connection");
                    String command = directive.substring("ctx init ".length()).trim();
                    if (command.isEmpty() || "!+-".indexOf(command.charAt(0)) < 0)
                        throw invalid("ctx init requires an assertion/addition/deletion command");
                    int last = requests.size() - 1;
                    IContextFederation.SourceDependencyRequest dependency = requests.get(last);
                    List<String> commands = new ArrayList<String>(dependency.getInitialization());
                    commands.add(command);
                    requests.set(last, new IContextFederation.SourceDependencyRequest(dependency.getLocator(),
                            dependency.isExact() ? dependency.getExactRevision() : null, commands, dependency.getTrustGroup()));
                } else {
                    throw invalid(
                            "unsupported metadata directive: "
                                    + directive);
                }
                stripped.append(
                        blankPreservingLineEnd(line));
            } else {
                stripped.append(line);
            }
            start = end;
        }

        return new Parsed(
                stripped.toString(),
                present,
                requests);
    }

    static String canonicalHeader(
            List<IContextFederation.SourceDependency>
                    dependencies,
            String lineSeparator) {
        if (dependencies == null
                || lineSeparator == null) {
            throw new NullPointerException();
        }
        StringBuilder result =
                new StringBuilder();
        if (dependencies.isEmpty()) {
            result.append(
                    "//! ctx dependencies none")
                    .append(lineSeparator);
            return result.toString();
        }

        for (IContextFederation.SourceDependency dependency
                : dependencies) {
            result.append(
                    "//! ctx connect ")
                    .append(
                            quoteLocator(
                                    dependency.getLocator()))
                    .append('@')
                    .append(
                            dependency.getRevision());
            if (dependency.getTrustGroup() != null) result.append(" trust ").append(quoteLocator(dependency.getTrustGroup()));
            result.append(lineSeparator);
            for (String command : dependency.getInitialization()) {
                boolean first = true;
                for (String line : command.split("\r?\n", -1)) {
                    result.append(first ? "//! ctx init " : "//! ctx init+ ").append(line).append(lineSeparator);
                    first = false;
                }
            }
        }
        return result.toString();
    }

    private static IContextFederation.SourceDependencyRequest
            parseDependency(String operand) {
        if (operand.isEmpty()) {
            throw invalid(
                    "ctx connect requires a Context locator");
        }

        String trustGroup = null;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < operand.length(); i++) {
            char c = operand.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (quoted && c == '\\') { escaped = true; continue; }
            if (c == '"') { quoted = !quoted; continue; }
            if (!quoted && operand.startsWith(" trust ", i)) {
                trustGroup = TrustGroups.validate(unquoteLocator(operand.substring(i + 7).trim()));
                operand = operand.substring(0, i).trim();
                break;
            }
        }
        int at = operand.lastIndexOf('@');
        Long revision = null;
        String locator = operand;
        if (at > 0
                && at + 1 < operand.length()) {
            String suffix =
                    operand.substring(at + 1);
            if (digits(suffix)) {
                try {
                    revision =
                            Long.valueOf(
                                    Long.parseLong(suffix));
                } catch (NumberFormatException failure) {
                    throw invalid(
                            "invalid exact RevisionId in "
                                    + operand);
                }
                locator =
                        operand.substring(0, at)
                                .trim();
            }
        }

        locator = unquoteLocator(locator);
        return new IContextFederation.SourceDependencyRequest(
                locator,
                revision, Collections.<String>emptyList(), trustGroup);
    }

    private static boolean digits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String unquoteLocator(
            String source) {
        String value = source.trim();
        if (value.isEmpty()) {
            throw invalid(
                    "ctx connect requires a Context locator");
        }
        if (value.charAt(0) != '"') {
            if (value.indexOf(' ') >= 0
                    || value.indexOf('\t') >= 0) {
                throw invalid(
                        "Context locator containing whitespace must be quoted");
            }
            return value;
        }
        if (value.length() < 2
                || value.charAt(
                        value.length() - 1) != '"') {
            throw invalid(
                    "unterminated quoted Context locator");
        }

        String body =
                value.substring(
                        1,
                        value.length() - 1);
        StringBuilder result =
                new StringBuilder(body.length());
        boolean escape = false;
        for (int i = 0; i < body.length(); ++i) {
            char c = body.charAt(i);
            if (escape) {
                if (c != '"' && c != '\\') {
                    throw invalid(
                            "unsupported escape in Context locator");
                }
                result.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else {
                result.append(c);
            }
        }
        if (escape) {
            throw invalid(
                    "unterminated escape in Context locator");
        }
        if (result.length() == 0) {
            throw invalid(
                    "Context locator must not be empty");
        }
        return result.toString();
    }

    private static String quoteLocator(
            String locator) {
        boolean quote = false;
        for (int i = 0; i < locator.length(); ++i) {
            char c = locator.charAt(i);
            if (Character.isWhitespace(c)
                    || c == '"'
                    || c == '\\') {
                quote = true;
                break;
            }
        }
        if (!quote) {
            return locator;
        }

        StringBuilder result =
                new StringBuilder(locator.length() + 2);
        result.append('"');
        for (int i = 0; i < locator.length(); ++i) {
            char c = locator.charAt(i);
            if (c == '"' || c == '\\') {
                result.append('\\');
            }
            result.append(c);
        }
        result.append('"');
        return result.toString();
    }

    private static String blankPreservingLineEnd(
            String line) {
        StringBuilder result =
                new StringBuilder(line.length());
        for (int i = 0; i < line.length(); ++i) {
            char c = line.charAt(i);
            result.append(
                    c == '\r' || c == '\n'
                            ? c
                            : ' ');
        }
        return result.toString();
    }

    private static IllegalArgumentException invalid(
            String message) {
        return new IllegalArgumentException(
                "Invalid KANGER Context source metadata: "
                        + message);
    }

    static final class Parsed {

        private final String source;
        private final boolean present;
        private final List<IContextFederation.SourceDependencyRequest>
                requests;

        private Parsed(
                String source,
                boolean present,
                List<IContextFederation.SourceDependencyRequest>
                        requests) {
            this.source = source;
            this.present = present;
            this.requests =
                    Collections.unmodifiableList(
                            new ArrayList<IContextFederation.SourceDependencyRequest>(
                                    requests));
        }

        String getSource() {
            return source;
        }

        boolean isPresent() {
            return present;
        }

        List<IContextFederation.SourceDependencyRequest>
                getRequests() {
            return requests;
        }
    }
}
