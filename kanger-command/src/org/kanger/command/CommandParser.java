/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import org.kanger.command.CommandRegistry.Family;
import org.kanger.command.CommandRegistry.Keyword;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.kanger.command.CommandParseException.Reason.*;

/**
 * Shared parser for the canonical KANGER command dialogue.
 *
 * <p>The parser performs only lexical/grammar canonicalization. Runtime object
 * existence, namespace legality, lifecycle preconditions and execution remain
 * downstream responsibilities.</p>
 */
public final class CommandParser {

    public CommandInvocation parse(String input) throws CommandParseException {
        if (input == null || input.trim().isEmpty()) {
            throw error(UNKNOWN_KEYWORD, "Empty command");
        }

        String line = input.trim();
        char first = line.charAt(0);
        if (first == '!' && line.length() > 1 && line.charAt(1) == '!') {
            throw error(INVALID_GRAMMAR,
                    "Unexpected '!' after Core statement operator");
        }
        if ("!?+-=".indexOf(first) >= 0) {
            return CommandInvocation.coreLanguage(line);
        }

        String expanded = CommandRegistry.expandAlias(line);
        if (!expanded.equals(line)) {
            CommandInvocation canonical = parse(expanded);
            return CommandInvocation.command(
                    canonical.getIntent(), canonical.getArguments(), line);
        }

        List<Token> prefix = tokenize(line, 2, false);
        if (prefix.isEmpty()) {
            throw error(UNKNOWN_KEYWORD, "Empty command");
        }

        Family family = CommandRegistry.resolveFamily(prefix.get(0).value);

        if (family == Family.RULE && prefix.size() > 1
                && resolvesTo(family, prefix.get(1).value, Keyword.COMMENT)) {
            return parseRuleComment(line);
        }
        if (family == Family.VALUES && prefix.size() > 1
                && resolvesTo(family, prefix.get(1).value, Keyword.ORDER)) {
            return parseValuesOrder(line);
        }

        List<Token> tokens = tokenize(line, Integer.MAX_VALUE, true);
        switch (family) {
            case RULE:
                return parseRule(line, tokens);
            case FUNCTION:
                return parseFunction(line, tokens);
            case BASE:
                return parseBase(line, tokens);
            case PREDICATE:
                return parsePredicateFamily(line, tokens);
            case VALUES:
                return parseValues(line, tokens);
            case SOLUTION:
                return parseSolution(line, tokens);
            case WHEN:
                return parseWhen(line, tokens);
            case TRANSACTION:
                return parseTransaction(line, tokens);
            case GET:
                return parseOptionalSingleArgument(
                        line, tokens, CommandIntent.SOURCE_GET, "source");
            case PUT:
                return parseSingleArgument(line, tokens, CommandIntent.SOURCE_PUT, "source");
            case DELETE:
                return parseOptionalSingleArgument(
                        line, tokens, CommandIntent.SOURCE_DELETE, "source");
            case STORAGE:
                return parseStorage(line, tokens);
            case ERASE:
                return parseNoArguments(line, tokens, CommandIntent.ERASE);
            case HELP:
                return parseNoArguments(line, tokens, CommandIntent.HELP);
            case QUIT:
                return parseNoArguments(line, tokens, CommandIntent.QUIT);
            default:
                throw error(UNKNOWN_KEYWORD, "Unknown command family");
        }
    }

    private CommandInvocation parseRule(String raw, List<Token> tokens)
            throws CommandParseException {
        String head = tokens.get(0).value;
        if (tokens.size() == 1) {
            if (CommandRegistry.isExactPluralFamilyWord(Family.RULE, head)) {
                return CommandInvocation.command(CommandIntent.RULE_ALL, raw);
            }
            return CommandInvocation.command(CommandIntent.RULE_STATUS, raw);
        }
        if (CommandRegistry.isExactPluralFamilyWord(Family.RULE, head)) {
            throw error(INVALID_GRAMMAR, "rules is the collection form");
        }

        String selector = tokens.get(1).value;
        if (CommandRegistry.isExact(selector, "show")) {
            throw error(INVALID_GRAMMAR, "rule show is not canonical");
        }

        Keyword keyword = tryResolve(Family.RULE, selector,
                Keyword.ALL, Keyword.PRODUCED, Keyword.LEVEL, Keyword.TREE);
        if (keyword == null) {
            if (tokens.size() != 2) {
                throw error(INVALID_GRAMMAR, "Invalid rule production");
            }
            return commandWithLong(CommandIntent.RULE_SHOW, "id", selector, raw);
        }

        switch (keyword) {
            case ALL:
                requireSize(tokens, 2);
                return CommandInvocation.command(CommandIntent.RULE_ALL, raw);
            case PRODUCED:
                requireSize(tokens, 2);
                return CommandInvocation.command(CommandIntent.RULE_PRODUCED, raw);
            case LEVEL:
                if (tokens.size() == 2) {
                    return CommandInvocation.command(CommandIntent.RULE_LEVEL, raw);
                }
                requireSize(tokens, 3);
                return commandWithLong(CommandIntent.RULE_LEVEL, "level", tokens.get(2).value, raw);
            case TREE:
                requireRequiredArgument(tokens, 3);
                requireSize(tokens, 3);
                return commandWithLong(CommandIntent.RULE_TREE, "id", tokens.get(2).value, raw);
            default:
                throw error(INVALID_GRAMMAR, "Invalid rule selector");
        }
    }

    private CommandInvocation parseRuleComment(String raw)
            throws CommandParseException {
        List<Token> tokens = tokenize(raw, 3, false);
        if (tokens.size() < 3) {
            throw error(MISSING_ARGUMENT, "rule comment requires rule id");
        }
        long id = parseNonNegativeLong(tokens.get(2).value, "rule id");
        Map<String, Object> arguments = args("id", id);
        String tail = tailAfter(raw, tokens.get(2).end);
        if (tail.isEmpty()) {
            return CommandInvocation.command(CommandIntent.RULE_COMMENT_GET, arguments, raw);
        }
        arguments.put("text", "\"\"".equals(tail) ? "" : tail);
        return CommandInvocation.command(CommandIntent.RULE_COMMENT_SET, arguments, raw);
    }

    private CommandInvocation parseFunction(String raw, List<Token> tokens)
            throws CommandParseException {
        String head = tokens.get(0).value;
        if (tokens.size() == 1) {
            if (CommandRegistry.isExactSingularFamilyWord(Family.FUNCTION, head)) {
                throw error(MISSING_ARGUMENT, "function requires id");
            }
            return CommandInvocation.command(CommandIntent.FUNCTIONS, raw);
        }
        if (CommandRegistry.isExactPluralFamilyWord(Family.FUNCTION, head)) {
            throw error(INVALID_GRAMMAR, "functions is the collection form");
        }
        if (resolvesTo(Family.FUNCTION, tokens.get(1).value, Keyword.SOURCE)) {
            requireRequiredArgument(tokens, 3);
            requireSize(tokens, 3);
            return commandWithLong(CommandIntent.FUNCTION_SOURCE, "id", tokens.get(2).value, raw);
        }
        if (CommandRegistry.isExact(tokens.get(1).value, "show")) {
            throw error(INVALID_GRAMMAR, "function show is not canonical");
        }
        requireSize(tokens, 2);
        return commandWithLong(CommandIntent.FUNCTION_SHOW, "id", tokens.get(1).value, raw);
    }

    private CommandInvocation parseBase(String raw, List<Token> tokens)
            throws CommandParseException {
        if (tokens.size() == 1) {
            return CommandInvocation.command(CommandIntent.BASE_STATUS, raw);
        }
        Keyword keyword = tryResolve(Family.BASE, tokens.get(1).value,
                Keyword.PREDICATE, Keyword.TREE);
        if (keyword == null) {
            throw error(INVALID_GRAMMAR, "Invalid base production");
        }
        if (keyword == Keyword.TREE) {
            requireRequiredArgument(tokens, 3);
            requireSize(tokens, 3);
            return commandWithLong(CommandIntent.BASE_TREE, "statementId", tokens.get(2).value, raw);
        }

        if (tokens.size() == 2) {
            if (CommandRegistry.isExact(tokens.get(1).value, "predicate")) {
                throw error(MISSING_ARGUMENT, "base predicate requires id or name");
            }
            return CommandInvocation.command(CommandIntent.BASE_PREDICATES, raw);
        }
        if (CommandRegistry.isExact(tokens.get(1).value, "predicates")) {
            throw error(INVALID_GRAMMAR, "base predicates is the collection form");
        }
        requireSize(tokens, 3);
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("predicate", predicateReference(tokens.get(2).value));
        return CommandInvocation.command(CommandIntent.BASE_PREDICATE, arguments, raw);
    }

    private CommandInvocation parsePredicateFamily(String raw,
                                                    List<Token> tokens)
            throws CommandParseException {
        if (tokens.size() == 1) {
            return CommandInvocation.command(CommandIntent.BASE_PREDICATES, raw);
        }
        requireSize(tokens, 2);
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("predicate", predicateReference(tokens.get(1).value));
        return CommandInvocation.command(
                CommandIntent.BASE_PREDICATE, arguments, raw);
    }

    private CommandInvocation parseValues(String raw, List<Token> tokens)
            throws CommandParseException {
        if (tokens.size() == 1) {
            return CommandInvocation.command(CommandIntent.VALUES, raw);
        }
        throw error(INVALID_GRAMMAR, "Invalid values production");
    }

    private CommandInvocation parseValuesOrder(String raw)
            throws CommandParseException {
        List<Token> prefix = tokenize(raw, 2, false);
        String tail = tailAfter(raw, prefix.get(1).end);
        if (tail.isEmpty()) {
            throw error(MISSING_ARGUMENT, "values order requires at least one field");
        }

        List<String> clauses = splitSortClauses(tail);
        List<SortKey> keys = new ArrayList<SortKey>();
        for (String clause : clauses) {
            List<Token> parts = tokenize(clause, Integer.MAX_VALUE, true);
            if (parts.isEmpty()) {
                throw error(MISSING_ARGUMENT, "Empty values sort key");
            }
            if (parts.size() > 2) {
                throw error(INVALID_GRAMMAR, "Sort keys must be separated by commas");
            }
            SortKey.Direction direction = SortKey.Direction.ASC;
            if (parts.size() == 2) {
                Keyword keyword;
                try {
                    keyword = CommandRegistry.resolveKeyword(
                            Family.VALUES, parts.get(1).value,
                            Keyword.ASC, Keyword.DESC);
                } catch (CommandParseException rejected) {
                    if (rejected.getReason() == AMBIGUOUS_PREFIX) {
                        throw rejected;
                    }
                    throw error(INVALID_GRAMMAR,
                            "Expected asc or desc after values sort field");
                }
                direction = keyword == Keyword.DESC
                        ? SortKey.Direction.DESC : SortKey.Direction.ASC;
            }
            keys.add(new SortKey(parts.get(0).value, direction));
        }

        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("keys", Collections.unmodifiableList(keys));
        return CommandInvocation.command(CommandIntent.VALUES_ORDER, arguments, raw);
    }

    private CommandInvocation parseSolution(String raw, List<Token> tokens)
            throws CommandParseException {
        String head = tokens.get(0).value;
        if (tokens.size() == 1) {
            if (CommandRegistry.isExactSingularFamilyWord(Family.SOLUTION, head)) {
                throw error(MISSING_ARGUMENT, "solution requires id");
            }
            return CommandInvocation.command(CommandIntent.SOLUTIONS, raw);
        }
        if (CommandRegistry.isExactPluralFamilyWord(Family.SOLUTION, head)) {
            throw error(INVALID_GRAMMAR, "solutions is the collection form");
        }
        if (CommandRegistry.isExact(tokens.get(1).value, "show")) {
            throw error(INVALID_GRAMMAR, "solution show is not canonical");
        }
        if (resolvesTo(Family.SOLUTION, tokens.get(1).value, Keyword.TREE)) {
            requireRequiredArgument(tokens, 3);
            requireSize(tokens, 3);
            return commandWithLong(CommandIntent.SOLUTION_TREE, "id", tokens.get(2).value, raw);
        }
        requireSize(tokens, 2);
        return commandWithLong(CommandIntent.SOLUTION_SHOW, "id", tokens.get(1).value, raw);
    }

    private CommandInvocation parseWhen(String raw, List<Token> tokens)
            throws CommandParseException {
        if (tokens.size() == 1) {
            return CommandInvocation.command(CommandIntent.WHEN_STATUS, raw);
        }
        Keyword keyword = tryResolve(Family.WHEN, tokens.get(1).value, Keyword.ACCEPT);
        if (keyword == null) {
            throw error(INVALID_GRAMMAR, "Invalid when production");
        }
        requireRequiredArgument(tokens, 3);
        requireSize(tokens, 3);
        long index = parseNonNegativeLong(tokens.get(2).value, "hypothesis index");
        return CommandInvocation.command(
                CommandIntent.WHEN_ACCEPT,
                args("index", index),
                raw);
    }

    private CommandInvocation parseTransaction(String raw, List<Token> tokens)
            throws CommandParseException {
        if (tokens.size() == 1) {
            return CommandInvocation.command(CommandIntent.TX_STATUS, raw);
        }
        if (CommandRegistry.isExact(tokens.get(1).value, "create")) {
            throw error(INVALID_GRAMMAR, "transaction create is not canonical");
        }
        Keyword keyword = CommandRegistry.resolveKeyword(
                Family.TRANSACTION, tokens.get(1).value,
                Keyword.START, Keyword.COMMIT, Keyword.ROLLBACK, Keyword.SQUASH);
        requireSize(tokens, 2);
        switch (keyword) {
            case START:
                return CommandInvocation.command(CommandIntent.TX_START, raw);
            case COMMIT:
                return CommandInvocation.command(CommandIntent.TX_COMMIT, raw);
            case ROLLBACK:
                return CommandInvocation.command(CommandIntent.TX_ROLLBACK, raw);
            case SQUASH:
                return CommandInvocation.command(CommandIntent.TX_SQUASH, raw);
            default:
                throw error(INVALID_GRAMMAR, "Invalid transaction action");
        }
    }

    private CommandInvocation parseStorage(String raw, List<Token> tokens)
            throws CommandParseException {
        if (tokens.size() == 1) {
            return CommandInvocation.command(CommandIntent.STORAGE_STATUS, raw);
        }
        Keyword keyword;
        try {
            keyword = CommandRegistry.resolveKeyword(
                    Family.STORAGE, tokens.get(1).value,
                    Keyword.USE, Keyword.CLOSE, Keyword.DROP, Keyword.REINDEX);
        } catch (CommandParseException rejected) {
            if (rejected.getReason() == AMBIGUOUS_PREFIX) {
                throw rejected;
            }
            throw error(INVALID_GRAMMAR, "Invalid storage production");
        }
        switch (keyword) {
            case CLOSE:
                requireSize(tokens, 2);
                return CommandInvocation.command(CommandIntent.STORAGE_CLOSE, raw);
            case USE:
                requireRequiredArgument(tokens, 3);
                requireSize(tokens, 3);
                return CommandInvocation.command(
                        CommandIntent.STORAGE_USE,
                        args("name", tokens.get(2).value), raw);
            case DROP:
                requireRequiredArgument(tokens, 3);
                requireSize(tokens, 3);
                return CommandInvocation.command(
                        CommandIntent.STORAGE_DROP,
                        args("name", tokens.get(2).value), raw);
            case REINDEX:
                requireRequiredArgument(tokens, 3);
                requireSize(tokens, 3);
                return CommandInvocation.command(
                        CommandIntent.STORAGE_REINDEX,
                        args("name", tokens.get(2).value), raw);
            default:
                throw error(INVALID_GRAMMAR, "Invalid storage action");
        }
    }

    private CommandInvocation parseOptionalSingleArgument(String raw,
                                                          List<Token> tokens,
                                                          CommandIntent intent,
                                                          String argumentName)
            throws CommandParseException {
        requireSize(tokens, tokens.size() == 1 ? 1 : 2);
        return CommandInvocation.command(
                intent,
                args(argumentName, tokens.size() == 1 ? "" : tokens.get(1).value),
                raw);
    }

    private CommandInvocation parseSingleArgument(String raw,
                                                  List<Token> tokens,
                                                  CommandIntent intent,
                                                  String argumentName)
            throws CommandParseException {
        requireRequiredArgument(tokens, 2);
        requireSize(tokens, 2);
        return CommandInvocation.command(
                intent,
                args(argumentName, tokens.get(1).value),
                raw);
    }

    private CommandInvocation parseNoArguments(String raw,
                                               List<Token> tokens,
                                               CommandIntent intent)
            throws CommandParseException {
        requireSize(tokens, 1);
        return CommandInvocation.command(intent, raw);
    }

    private CommandInvocation commandWithLong(CommandIntent intent,
                                              String name,
                                              String value,
                                              String raw)
            throws CommandParseException {
        return CommandInvocation.command(
                intent,
                args(name, parseNonNegativeLong(value, name)),
                raw);
    }

    private Object predicateReference(String value) throws CommandParseException {
        if (value != null && !value.isEmpty() && Character.isDigit(value.charAt(0))) {
            return parseNonNegativeLong(value, "predicate id");
        }
        return value;
    }

    private long parseNonNegativeLong(String value, String label)
            throws CommandParseException {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new NumberFormatException("negative");
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw error(INVALID_ARGUMENT_SHAPE,
                    "Invalid " + label + " " + value);
        }
    }

    private Keyword tryResolve(Family family,
                               String token,
                               Keyword... allowed)
            throws CommandParseException {
        try {
            return CommandRegistry.resolveKeyword(family, token, allowed);
        } catch (CommandParseException rejected) {
            if (rejected.getReason() == UNKNOWN_KEYWORD) {
                return null;
            }
            throw rejected;
        }
    }

    private boolean resolvesTo(Family family, String token, Keyword expected)
            throws CommandParseException {
        Keyword resolved = tryResolve(family, token, expected);
        return resolved == expected;
    }

    private void requireRequiredArgument(List<Token> tokens, int requiredSize)
            throws CommandParseException {
        if (tokens.size() < requiredSize) {
            throw error(MISSING_ARGUMENT, "Missing required argument");
        }
    }

    private void requireSize(List<Token> tokens, int expected)
            throws CommandParseException {
        if (tokens.size() < expected) {
            throw error(MISSING_ARGUMENT, "Missing required argument");
        }
        if (tokens.size() > expected) {
            throw error(EXTRA_ARGUMENT, "Unexpected extra argument");
        }
    }

    private Map<String, Object> args(String name, Object value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(name, value);
        return result;
    }

    private List<String> splitSortClauses(String tail)
            throws CommandParseException {
        List<String> result = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < tail.length(); ++i) {
            char c = tail.charAt(i);
            if (quoted) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
                current.append(c);
            } else if (c == ',') {
                String clause = current.toString().trim();
                if (clause.isEmpty()) {
                    throw error(INVALID_GRAMMAR, "Empty values sort clause");
                }
                result.add(clause);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw error(UNTERMINATED_QUOTE, "Unterminated quote in values order");
        }
        String last = current.toString().trim();
        if (last.isEmpty()) {
            throw error(INVALID_GRAMMAR, "Empty values sort clause");
        }
        result.add(last);
        return result;
    }

    private String tailAfter(String line, int position) {
        int p = position;
        while (p < line.length() && Character.isWhitespace(line.charAt(p))) {
            ++p;
        }
        return p >= line.length() ? "" : line.substring(p);
    }

    private List<Token> tokenize(String line, int maxTokens, boolean validateRest)
            throws CommandParseException {
        List<Token> result = new ArrayList<Token>();
        int p = 0;
        while (p < line.length() && result.size() < maxTokens) {
            while (p < line.length() && Character.isWhitespace(line.charAt(p))) {
                ++p;
            }
            if (p >= line.length()) {
                break;
            }

            int start = p;
            StringBuilder value = new StringBuilder();
            if (line.charAt(p) == '"') {
                ++p;
                boolean closed = false;
                while (p < line.length()) {
                    char c = line.charAt(p++);
                    if (c == '"') {
                        closed = true;
                        break;
                    }
                    if (c == '\\' && p < line.length()) {
                        char next = line.charAt(p);
                        if (next == '"' || next == '\\') {
                            value.append(next);
                            ++p;
                        } else {
                            value.append(c);
                        }
                    } else {
                        value.append(c);
                    }
                }
                if (!closed) {
                    throw error(UNTERMINATED_QUOTE, "Unterminated quoted argument");
                }
                if (p < line.length() && !Character.isWhitespace(line.charAt(p))) {
                    throw error(INVALID_GRAMMAR,
                            "Quoted argument must end at a token boundary");
                }
            } else {
                while (p < line.length() && !Character.isWhitespace(line.charAt(p))) {
                    value.append(line.charAt(p++));
                }
            }
            result.add(new Token(value.toString(), start, p));
        }

        if (validateRest) {
            while (p < line.length() && Character.isWhitespace(line.charAt(p))) {
                ++p;
            }
            if (p < line.length()) {
                throw error(EXTRA_ARGUMENT, "Unexpected trailing input");
            }
        }
        return result;
    }

    private CommandParseException error(CommandParseException.Reason reason,
                                        String message) {
        return new CommandParseException(reason, message);
    }

    private static final class Token {
        private final String value;
        private final int start;
        private final int end;

        private Token(String value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }
}
