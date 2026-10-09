/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.internal.IContextFederation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detached, read-only projection of native rule views, with context-scoped IDs. */
public final class ContextRuleInspection {
    private ContextRuleInspection() { }
    public static IContextFederation.RuleBlock inspect(Mind mind, String locator,
            IContextFederation.Revision revision, boolean working,
            IContextFederation.RuleSelection selection, Long number) throws Exception {
        List<IContextFederation.RuleRow> rows = new ArrayList<IContextFederation.RuleRow>();
        for (IRule rule : mind.getRules()) {
            if (rule.isDeleted(mind)) continue;
            boolean selected = number != null ? rule.getId() == number.longValue()
                    : selection == IContextFederation.RuleSelection.ALL
                    || (selection == IContextFederation.RuleSelection.PRODUCED
                            ? rule.isGenerated() : !rule.isGenerated());
            if (selected) {
                rows.add(row(mind, rule, selection));
            }
        }
        if (number != null && rows.isEmpty()) {
            throw new CommandErrorException("Rule not found " + locator + "@"
                    + revision.getRevision() + ":" + number);
        }
        return new IContextFederation.RuleBlock(locator, revision, working, rows);
    }
    private static IContextFederation.RuleRow row(Mind mind,IRule rule,
            IContextFederation.RuleSelection selection) throws Exception {
        String statement = rule.getOrigin();
        if (statement == null || statement.isEmpty()) statement = rule.toString();
        return new IContextFederation.RuleRow(rule.getId(), statement,
                rule.isGenerated(), rule.getComment(), selection == IContextFederation.RuleSelection.TREE
                ? mind.formatTree(rule) : Collections.<List<String>>emptyList());
    }
}
