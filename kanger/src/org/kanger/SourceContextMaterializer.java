/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.factory.CommentFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.IRule;
import org.kanger.units.Comment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Reconstructs a compilable KANGER source document from the effective semantic
 * state of one published level-0 Mind.
 *
 * <p>This is deliberately different from an exact source-document snapshot.
 * Exact source bytes belong to the source/editor boundary; this class
 * materializes the current semantic context afresh. Generated rules and
 * deleted units are excluded. Active UDF declarations are emitted before
 * rules so a round-trip into an empty Mind can resolve user-defined functions.
 * Stored header/footer comments and per-rule comments are retained.</p>
 */
public final class SourceContextMaterializer {

    private SourceContextMaterializer() {
    }

    public static String materializeLevelZero(IMind mind) throws Exception {
        if (mind == null) {
            throw new IllegalArgumentException("Mind is required");
        }
        if (mind.getTransactionLevel() != 0) {
            throw new IllegalStateException(
                    "Source context materialization requires transaction level 0");
        }

        Mind context = (Mind) mind;
        StringBuilder out = new StringBuilder();

        appendComment(out, context, CommentFactory.HEADER_ID, false);

        List<IOperation> operations = new ArrayList<IOperation>();
        for (IOperation operation : context.getLibrary()) {
            if (!operation.isDeleted(context)) {
                operations.add(operation);
            }
        }
        Collections.sort(operations, new Comparator<IOperation>() {
            @Override
            public int compare(IOperation left, IOperation right) {
                return left.asString().compareTo(right.asString());
            }
        });
        for (IOperation operation : operations) {
            appendBlock(out, operation.asString(), true);
        }

        SortedMap<Long, IRule> rules = new TreeMap<Long, IRule>();
        for (IRule rule : context.getRules()) {
            if (!rule.isGenerated() && !rule.isDeleted(context)) {
                rules.put(rule.getId(), rule);
            }
        }
        for (IRule rule : rules.values()) {
            appendComment(out, context, rule.getId(), true);
            appendBlock(out, rule.getOrigin(), true);
        }

        appendComment(out, context, CommentFactory.FOOTER_ID, true);
        return out.toString();
    }

    private static void appendComment(StringBuilder out,
                                      Mind context,
                                      long id,
                                      boolean separate) throws Exception {
        Comment comment = context.getComments().get(id);
        if (comment == null || comment.isDeleted(context)
                || comment.getComment() == null || comment.getComment().isEmpty()) {
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
