/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.util.List;

/**
 * Formats a parsed invocation with full canonical command spelling.
 *
 * <p>Formatting occurs after prefix/grammar normalization. The formatter is
 * presentation-only and contains no runtime dispatch behavior.</p>
 */
public final class CommandFormatter {

    public String format(CommandInvocation invocation) {
        if (invocation == null) {
            throw new IllegalArgumentException("invocation must not be null");
        }
        if (invocation.isCoreLanguage()) {
            return invocation.getRaw();
        }

        switch (invocation.getIntent()) {
            case RULE_STATUS:
                return "rule";
            case RULE_SHOW:
                return "rule " + number(invocation, "id");
            case RULE_ALL:
                return "rule all";
            case RULE_PRODUCED:
                return "rule produced";
            case RULE_LEVEL:
                return "rule level " + number(invocation, "level");
            case RULE_TREE:
                return "rule tree " + number(invocation, "id");
            case RULE_COMMENT_GET:
                return "rule comment " + number(invocation, "id");
            case RULE_COMMENT_SET:
                return "rule comment " + number(invocation, "id") + " "
                        + commentText(invocation.getArgument("text"));

            case FUNCTIONS:
                return "functions";
            case FUNCTION_SHOW:
                return "function " + number(invocation, "id");
            case FUNCTION_SOURCE:
                return "function source " + number(invocation, "id");

            case BASE_STATUS:
                return "base";
            case BASE_PREDICATES:
                return "base predicates";
            case BASE_PREDICATE:
                return "base predicate " + argument(invocation.getArgument("predicate"));
            case BASE_TREE:
                return "base tree " + number(invocation, "statementId");

            case VALUES:
                return "values";
            case VALUES_ORDER:
                return formatValuesOrder(invocation);

            case SOLUTIONS:
                return "solutions";
            case SOLUTION_SHOW:
                return "solution " + number(invocation, "id");
            case SOLUTION_TREE:
                return "solution tree " + number(invocation, "id");

            case WHEN_STATUS:
                return "when";
            case WHEN_ACCEPT:
                return "when accept " + number(invocation, "index");

            case TX_STATUS:
                return "transaction";
            case TX_START:
                return "transaction start";
            case TX_COMMIT:
                return "transaction commit";
            case TX_ROLLBACK:
                return "transaction rollback";

            case SOURCE_GET:
                return optionalArgumentCommand("get", invocation.getArgument("source"));
            case SOURCE_PUT:
                return "put " + argument(invocation.getArgument("source"));
            case SOURCE_DELETE:
                return "delete " + argument(invocation.getArgument("source"));

            case STORAGE_STATUS:
                return "storage";
            case STORAGE_USE:
                return "storage use " + argument(invocation.getArgument("name"));
            case STORAGE_CLOSE:
                return "storage close";
            case STORAGE_DROP:
                return "storage drop " + argument(invocation.getArgument("name"));
            case STORAGE_REINDEX:
                return "storage reindex " + argument(invocation.getArgument("name"));

            case ERASE:
                return "erase";
            case HELP:
                return "help";
            case QUIT:
                return "quit";
            default:
                throw new IllegalArgumentException(
                        "No canonical formatter for " + invocation.getIntent());
        }
    }

    @SuppressWarnings("unchecked")
    private String formatValuesOrder(CommandInvocation invocation) {
        Object value = invocation.getArgument("keys");
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("VALUES_ORDER requires SortKey list");
        }
        List<SortKey> keys = (List<SortKey>) value;
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("VALUES_ORDER requires at least one SortKey");
        }
        StringBuilder out = new StringBuilder("values order ");
        for (int i = 0; i < keys.size(); ++i) {
            if (i > 0) {
                out.append(", ");
            }
            SortKey key = keys.get(i);
            out.append(argument(key.getField(), true))
                    .append(' ')
                    .append(key.getDirection() == SortKey.Direction.DESC
                            ? "desc" : "asc");
        }
        return out.toString();
    }

    private String optionalArgumentCommand(String command, Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return command;
        }
        return command + " " + argument(value);
    }

    private String number(CommandInvocation invocation, String name) {
        Object value = invocation.getArgument(name);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(name + " must be numeric");
        }
        return String.valueOf(((Number) value).longValue());
    }

    private String argument(Object value) {
        return argument(value, false);
    }

    private String argument(Object value, boolean commaIsStructural) {
        if (value == null) {
            throw new IllegalArgumentException("argument must not be null");
        }
        String text = String.valueOf(value);
        if (!requiresQuotes(text, commaIsStructural)) {
            return text;
        }
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private boolean requiresQuotes(String text, boolean commaIsStructural) {
        if (text.isEmpty() || (commaIsStructural && text.indexOf(',') >= 0)) {
            return true;
        }
        for (int i = 0; i < text.length(); ++i) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String commentText(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("comment text must not be null");
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? "\"\"" : text;
    }
}
