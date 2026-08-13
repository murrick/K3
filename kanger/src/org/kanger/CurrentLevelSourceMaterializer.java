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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Materializes the compilable declarative source owned by one Mind level. */
public final class CurrentLevelSourceMaterializer {

    private CurrentLevelSourceMaterializer() {
    }

    public static String materialize(IMind mind) throws Exception {
        if (mind == null) {
            throw new IllegalArgumentException("Mind is required");
        }
        Mind context = (Mind) mind;
        long ownerId = context.getId();
        StringBuilder out = new StringBuilder();

        appendComment(out, context, CommentFactory.HEADER_ID, false, ownerId);

        List<Operation> operations = new ArrayList<Operation>();
        for (Object candidate : context.getLibrary()) {
            Operation operation = (Operation) candidate;
            if (operation.getMindId() == ownerId && !operation.isDeleted(context)) {
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

        SortedMap<Long, IRule> rules = new TreeMap<Long, IRule>();
        for (IRule rule : context.getRules()) {
            if (rule.getMindId() == ownerId
                    && !rule.isGenerated()
                    && !rule.isDeleted(context)) {
                rules.put(rule.getId(), rule);
            }
        }
        for (IRule rule : rules.values()) {
            appendComment(out, context, rule.getId(), true, ownerId);
            appendBlock(out, rule.getOrigin(), true);
        }

        appendComment(out, context, CommentFactory.FOOTER_ID, true, ownerId);
        return out.toString();
    }

    private static void appendComment(StringBuilder out,
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
