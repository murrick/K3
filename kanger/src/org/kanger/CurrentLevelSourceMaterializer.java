/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.factory.CommentFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.units.Comment;
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Materializes the source-representable semantic delta of one explicit Mind
 * level relative to its immediate parent.
 *
 * <p>The projection is semantic, not provenance-based. Therefore a declaration
 * restored at the current level is included even when Core reuses an inherited
 * canonical unit whose {@code mindId} belongs to a lower level. Conversely,
 * unchanged parent declarations are not aggregated. Deletions and other
 * transaction-control delta without standalone compilable source syntax remain
 * live runtime state rather than being translated into invented {@code .k}
 * syntax.</p>
 */
public final class CurrentLevelSourceMaterializer {

    private CurrentLevelSourceMaterializer() {
    }

    public static String materialize(IMind mind) throws Exception {
        if (mind == null) {
            throw new IllegalArgumentException("Mind is required");
        }
        Mind context = (Mind) mind;
        Mind parent = (Mind) context.getNext();
        long ownerId = context.getId();
        StringBuilder out = new StringBuilder();

        appendOwnedComment(out, context, CommentFactory.HEADER_ID, false, ownerId);

        Map<String, Operation> parentOperations = parent == null
                ? Collections.<String, Operation>emptyMap()
                : activeOperations(parent);
        List<Operation> operations = new ArrayList<Operation>();
        for (Operation operation : activeOperations(context).values()) {
            Operation before = parentOperations.get(operation.toString());
            if (before == null || !operation.asString().equals(before.asString())) {
                operations.add(operation);
            }
        }
        Collections.sort(operations, new Comparator<Operation>() {
            @Override
            public int compare(Operation left, Operation right) {
                return left.asString().compareTo(right.asString());
            }
        });
        for (Operation operation : operations) {
            appendBlock(out, operation.asString(), true);
        }

        Map<String, Rule> parentRules = parent == null
                ? Collections.<String, Rule>emptyMap()
                : activePrimaryRules(parent);
        SortedMap<Long, Rule> rules = new TreeMap<Long, Rule>();
        for (Map.Entry<String, Rule> entry : activePrimaryRules(context).entrySet()) {
            if (!parentRules.containsKey(entry.getKey())) {
                rules.put(entry.getValue().getId(), entry.getValue());
            }
        }
        for (Rule rule : rules.values()) {
            appendOwnedComment(out, context, rule.getId(), true, ownerId);
            appendBlock(out, rule.getOrigin(), true);
        }

        appendOwnedComment(out, context, CommentFactory.FOOTER_ID, true, ownerId);
        return out.toString();
    }

    private static Map<String, Rule> activePrimaryRules(Mind mind) throws Exception {
        Map<String, Rule> rules = new LinkedHashMap<String, Rule>();
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (rule.isGenerated() || rule.isDeleted(mind)) {
                continue;
            }
            rules.put(rule.getOrigin(), rule);
        }
        return rules;
    }

    private static Map<String, Operation> activeOperations(Mind mind) throws Exception {
        Map<String, Operation> operations = new LinkedHashMap<String, Operation>();
        for (Object candidate : mind.getLibrary()) {
            Operation operation = (Operation) candidate;
            if (!operation.isDeleted(mind)) {
                operations.put(operation.toString(), operation);
            }
        }
        return operations;
    }

    private static void appendOwnedComment(StringBuilder out,
                                           Mind context,
                                           long id,
                                           boolean separate,
                                           long ownerId) throws Exception {
        Comment comment = context.getComments().get(id);
        if (comment == null
                || comment.getMindId() != ownerId
                || comment.isDeleted(context)
                || comment.getComment() == null
                || comment.getComment().isEmpty()) {
            return;
        }
        appendBlock(out, comment.getComment(), separate);
    }

    private static void appendBlock(StringBuilder out,
                                    String text,
                                    boolean separate) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (separate && out.length() > 0 && !endsWithBlankLine(out)) {
            out.append(Enums.LINE_SEPARATOR);
        }
        String[] lines = text.split("\\R", -1);
        for (String line : lines) {
            out.append(line).append(Enums.LINE_SEPARATOR);
        }
    }

    private static boolean endsWithBlankLine(StringBuilder out) {
        String separator = Enums.LINE_SEPARATOR;
        int one = out.length() - separator.length();
        int two = one - separator.length();
        return one >= 0 && two >= 0
                && out.substring(one).equals(separator)
                && out.substring(two, one).equals(separator);
    }
}
